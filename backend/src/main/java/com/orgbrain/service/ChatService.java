package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.*;
import com.orgbrain.dto.DtoMapper;
import com.orgbrain.dto.ProyectoDtos.ProyectoResumenDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.*;
import com.orgbrain.repository.ConversacionChatRepository;
import com.orgbrain.repository.TareaRepository;
import com.orgbrain.service.ia.ExtractorHeuristico;
import com.orgbrain.service.ia.GeminiClient;
import com.orgbrain.service.ia.GeminiException;
import com.orgbrain.util.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * Orquesta un turno de chat:
 * <ol>
 *   <li>[BD] Guarda el mensaje y reúne contexto: memoria, tareas, peticiones, proyectos, directorio, historial.</li>
 *   <li>[Gemini] Extrae JSON estructurado (peticiones, tareas propias, dependencias, memorias, consultas y cambios de
 *       proyectos y tareas). Sin clave o si falla: reglas.</li>
 *   <li>[BD] Aplica todo, recalcula prioridades y prepara el estado de los proyectos consultados o modificados.</li>
 *   <li>[Gemini] Redacta la respuesta confirmando lo hecho y contestando las consultas. Sin clave o si falla: plantilla.</li>
 *   <li>[BD] Guarda la respuesta.</li>
 * </ol>
 * Las llamadas a Gemini se hacen fuera de transacción para no retener conexiones de BD durante segundos.
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);
	private static final int MEMORIAS_RELEVANTES = 6;
	private static final int HISTORIAL = 10;
	private static final int TAREAS_EN_CONTEXTO = 15;
	private static final int MAX_PROYECTOS_DETALLADOS = 3;

	private final ConversacionChatRepository chatRepository;
	private final TareaRepository tareaRepository;
	private final EmpleadoService empleadoService;
	private final MemoriaService memoriaService;
	private final AsignacionService asignacionService;
	private final ProyectoService proyectoService;
	private final AccionesProyectoService accionesProyecto;
	private final MotorPriorizacionService motor;
	private final GeminiClient gemini;
	private final ExtractorHeuristico heuristico;
	private final TransactionTemplate tx;

	public ChatService(ConversacionChatRepository chatRepository, TareaRepository tareaRepository,
			EmpleadoService empleadoService, MemoriaService memoriaService, AsignacionService asignacionService,
			ProyectoService proyectoService, AccionesProyectoService accionesProyecto, MotorPriorizacionService motor,
			GeminiClient gemini, ExtractorHeuristico heuristico, PlatformTransactionManager transactionManager) {
		this.chatRepository = chatRepository;
		this.tareaRepository = tareaRepository;
		this.empleadoService = empleadoService;
		this.memoriaService = memoriaService;
		this.asignacionService = asignacionService;
		this.proyectoService = proyectoService;
		this.accionesProyecto = accionesProyecto;
		this.motor = motor;
		this.gemini = gemini;
		this.heuristico = heuristico;
		this.tx = new TransactionTemplate(transactionManager);
	}

	private record Acciones(List<TareaDto> tareasCreadas, List<TareaDto> tareasActualizadas,
			List<ProyectoResumenDto> proyectos, List<MemoriaDto> memoriasGuardadas, List<String> descripcion,
			List<String> avisos, String estadoProyectos) {
	}

	public ChatResponse procesarMensaje(Long empleadoId, ChatRequest req) {
		String mensaje = req.mensaje().trim();

		// 1) Contexto (y mensaje guardado).
		ContextoChat ctx = tx.execute(s -> {
			Empleado empleado = empleadoService.buscar(empleadoId);
			ContextoChat c = new ContextoChat(
					DtoMapper.empleado(empleado),
					memoriaService.buscarRelevantes(empleadoId, mensaje, MEMORIAS_RELEVANTES),
					tareasAbiertas(empleadoId),
					peticionesAbiertas(empleadoId),
					empleadoService.listarDominios(),
					historialReciente(empleadoId),
					proyectoService.todos().stream().map(Proyecto::getNombre).toList(),
					lineasProyectos(empleadoId),
					LocalDate.now());
			chatRepository.save(new ConversacionChat(empleado, mensaje, RolMensaje.USUARIO));
			return c;
		});

		// 2) Extracción estructurada.
		List<String> avisos = new ArrayList<>();
		ModoIa modo;
		ExtraccionDto extraccion;
		if (gemini.disponible()) {
			try {
				extraccion = gemini.extraer(mensaje, ctx);
				modo = ModoIa.GEMINI;
			} catch (GeminiException e) {
				log.warn("Extracción con Gemini fallida, se usan reglas: {}", e.getMessage());
				avisos.add("Gemini no disponible (" + e.getMessage() + "); mensaje procesado con reglas simples.");
				extraccion = heuristico.extraer(mensaje, ctx);
				modo = ModoIa.RESPALDO_SIN_IA;
			}
		} else {
			extraccion = heuristico.extraer(mensaje, ctx);
			modo = ModoIa.SIN_IA;
		}
		log.debug("Extracción ({}): {}", modo, extraccion);

		// 3) Acciones en BD.
		final ExtraccionDto ext = extraccion;
		Acciones acciones = tx.execute(s -> aplicar(empleadoId, mensaje, ext));
		avisos.addAll(acciones.avisos());

		// 4) Respuesta en lenguaje natural.
		String respuesta;
		if (modo == ModoIa.GEMINI) {
			try {
				respuesta = gemini.responder(mensaje, ctx, acciones.descripcion(), acciones.avisos(), acciones.estadoProyectos());
			} catch (GeminiException e) {
				log.warn("Respuesta con Gemini fallida, se usa plantilla: {}", e.getMessage());
				avisos.add("Gemini no pudo redactar la respuesta (" + e.getMessage() + ").");
				respuesta = respuestaPlantilla(ctx, acciones);
				modo = ModoIa.RESPALDO_SIN_IA;
			}
		} else {
			respuesta = respuestaPlantilla(ctx, acciones);
		}

		// 5) Guardar respuesta.
		final String textoRespuesta = respuesta;
		tx.executeWithoutResult(s -> chatRepository.save(
				new ConversacionChat(empleadoService.buscar(empleadoId), textoRespuesta, RolMensaje.ASISTENTE)));

		return new ChatResponse(respuesta, modo, extraccion, acciones.tareasCreadas(), acciones.tareasActualizadas(),
				acciones.proyectos(), acciones.memoriasGuardadas(), ctx.memoriasRelevantes(), acciones.descripcion(), avisos);
	}

	/** Una petición o tarea propia del mensaje, con lo que le ha pasado, para redactar la respuesta al final. */
	private static final class Elemento {
		final AsignacionService.Resultado resultado;
		final boolean esPeticion;
		final String objeto;
		final String esSubtareaDe;
		final String proyecto;
		Tarea padre;
		AsignacionService.AsignacionProyecto asignacion;

		Elemento(AsignacionService.Resultado resultado, boolean esPeticion, String objeto, String esSubtareaDe, String proyecto) {
			this.resultado = resultado;
			this.esPeticion = esPeticion;
			this.objeto = objeto;
			this.esSubtareaDe = esSubtareaDe;
			this.proyecto = proyecto;
		}

		Tarea tarea() {
			return resultado.tarea();
		}
	}

	/**
	 * Aplica la extracción por fases:
	 * <ol>
	 *   <li>Crea (o reutiliza si ya existen) las tareas propias y las peticiones, sin enlazar nada.</li>
	 *   <li>Asocia cada una a su proyecto (lo crea si es una petición para un proyecto nuevo).</li>
	 *   <li>Crea las dependencias explícitas que ha expresado el usuario.</li>
	 *   <li>Enlaza como subtarea solo lo que no bloquea ya a otra tarea de este mensaje, para que un enlace implícito
	 *       nunca impida una dependencia que el usuario ha pedido de forma explícita.</li>
	 *   <li>Cambios de proyectos y de tareas existentes.</li>
	 *   <li>Memoria, recálculo de prioridades, redacción y estado de los proyectos consultados o modificados.</li>
	 * </ol>
	 */
	private Acciones aplicar(Long empleadoId, String mensaje, ExtraccionDto extraccion) {
		Empleado empleado = empleadoService.buscar(empleadoId);
		List<Elemento> elementos = new ArrayList<>();
		List<MemoriaDto> memorias = new ArrayList<>();
		List<String> avisos = new ArrayList<>();
		List<String> descripcion = new ArrayList<>();
		Set<Proyecto> proyectosTocados = new LinkedHashSet<>();

		// 1) Crear o reutilizar.
		for (TareaPropiaExtraidaDto tp : extraccion.tareasPropias()) {
			elementos.add(new Elemento(asignacionService.crearTareaPropia(empleado, tp, mensaje), false, tp.titulo(),
					tp.esSubtareaDe(), tp.proyectoRelacionado()));
		}
		for (PeticionExtraidaDto p : extraccion.peticiones()) {
			AsignacionService.Resultado r = asignacionService.crearTareaParaPeticion(empleado, p, mensaje);
			if (r.aviso() != null) {
				avisos.add(r.aviso());
				memoriaService.guardar(empleado, TipoMemoria.NECESIDAD_PENDIENTE,
						"Necesita " + p.objeto() + " y no hay responsable claro en el directorio.", p.proyectoRelacionado())
						.ifPresent(memorias::add);
				continue;
			}
			elementos.add(new Elemento(r, true, p.objeto(), p.esSubtareaDe(), p.proyectoRelacionado()));
		}

		List<Tarea> delMensaje = new ArrayList<>();
		List<Tarea> creadas = new ArrayList<>();
		Map<String, Tarea> porReferencia = new HashMap<>();
		for (Elemento e : elementos) {
			if (!delMensaje.contains(e.tarea())) delMensaje.add(e.tarea());
			if (e.resultado.creada()) creadas.add(e.tarea());
			porReferencia.put(Texto.normalizar(e.objeto), e.tarea());
			porReferencia.put(Texto.normalizar(e.tarea().getTitulo()), e.tarea());
		}

		// 2) Proyecto. Primero las peticiones, que pueden crear el proyecto; las tareas propias se asocian si ya existe.
		List<Elemento> orden = new ArrayList<>(elementos.stream().filter(e -> e.esPeticion).toList());
		elementos.stream().filter(e -> !e.esPeticion).forEach(orden::add);
		for (Elemento e : orden) {
			e.asignacion = asignacionService.asignarProyecto(empleado, e.tarea(), e.proyecto, e.esPeticion);
			if (e.asignacion != null) proyectosTocados.add(e.asignacion.proyecto());
		}

		// 3) Dependencias explícitas.
		Set<Long> bloqueantesExplicitos = new HashSet<>();
		List<AsignacionService.ResultadoEnlace> enlacesExplicitos = new ArrayList<>();
		for (DependenciaExtraidaDto d : extraccion.dependencias()) {
			AsignacionService.ResultadoEnlace r = asignacionService.enlazarReferencias(empleado,
					referencia(d.bloqueante(), porReferencia), referencia(d.bloqueada(), porReferencia), delMensaje, creadas);
			if (r.bloqueante() != null && r.bloqueada() != null) {
				bloqueantesExplicitos.add(r.bloqueante().getId());
			}
			if (r.aviso() != null) {
				avisos.add(r.aviso());
			} else if (r.creado()) {
				enlacesExplicitos.add(r);
			}
		}

		// 4) Subtareas.
		for (Elemento e : elementos) {
			if (!bloqueantesExplicitos.contains(e.tarea().getId())) {
				e.padre = asignacionService.enlazarComoSubtarea(empleado, e.tarea(), e.esSubtareaDe);
			}
		}

		// 5) Cambios de proyectos y tareas existentes.
		AccionesProyectoService.Resultado cambiosProyecto =
				accionesProyecto.aplicarCambiosProyecto(empleado, extraccion.cambiosProyecto(), delMensaje);
		AccionesProyectoService.Resultado cambiosTarea =
				accionesProyecto.aplicarCambiosTarea(empleado, extraccion.cambiosTarea(), delMensaje);
		avisos.addAll(cambiosProyecto.avisos());
		avisos.addAll(cambiosTarea.avisos());
		proyectosTocados.addAll(cambiosProyecto.proyectos());
		proyectosTocados.addAll(cambiosTarea.proyectos());

		// 6) Memoria: un único recuerdo por proyecto con las peticiones nuevas (las abiertas ya van en el contexto).
		Map<String, List<Elemento>> peticionesPorProyecto = new LinkedHashMap<>();
		elementos.stream().filter(e -> e.esPeticion && e.resultado.creada()).forEach(e -> peticionesPorProyecto
				.computeIfAbsent(e.tarea().getProyecto() == null ? "" : e.tarea().getProyecto().getNombre(), k -> new ArrayList<>()).add(e));
		peticionesPorProyecto.forEach((proyecto, lista) -> {
			String resumen = (proyecto.isEmpty() ? "Pidió " : "Para " + proyecto + " pidió ")
					+ String.join("; ", lista.stream().map(e -> e.objeto + " (#" + e.tarea().getId() + ", "
					+ e.tarea().getResponsable().getNombre() + ")").toList()) + ".";
			memoriaService.guardar(empleado, TipoMemoria.NECESIDAD_PENDIENTE, resumen, proyecto.isEmpty() ? null : proyecto)
					.ifPresent(memorias::add);
		});
		for (MemoriaExtraidaDto m : extraccion.memorias()) {
			memoriaService.guardar(empleado, m.tipo(), m.contenido(), m.proyectoRelacionado()).ifPresent(memorias::add);
		}

		List<Tarea> actualizadas = new ArrayList<>();
		for (Elemento e : elementos) {
			boolean cambiada = e.resultado.urgenciaAnterior() != null || e.padre != null || e.asignacion != null;
			if (!e.resultado.creada() && cambiada && !actualizadas.contains(e.tarea())) actualizadas.add(e.tarea());
		}
		enlacesExplicitos.forEach(r -> anadirSiFalta(actualizadas, r.bloqueante(), creadas));
		cambiosProyecto.tareas().forEach(t -> anadirSiFalta(actualizadas, t, creadas));
		cambiosTarea.tareas().forEach(t -> anadirSiFalta(actualizadas, t, creadas));
		boolean hayCambios = !creadas.isEmpty() || !actualizadas.isEmpty() || !cambiosProyecto.acciones().isEmpty();
		if (hayCambios) {
			motor.recalcularTodo();
		}

		// Redacción, después de recalcular para dar la urgencia calculada final.
		for (Elemento e : elementos) {
			Tarea t = e.tarea();
			AsignacionService.Resultado r = e.resultado;
			if (r.creada() && e.esPeticion) {
				descripcion.add("Creada la tarea #" + t.getId() + " «" + t.getTitulo() + "» en el backlog de "
						+ t.getResponsable().getNombre() + " (" + t.getResponsable().getRol() + ", responsable de «"
						+ r.dominio().getNombre() + "») con urgencia propia " + t.getUrgenciaPropia()
						+ " y urgencia calculada " + t.getUrgenciaCalculada() + ".");
			} else if (r.creada()) {
				descripcion.add("Creada la tarea #" + t.getId() + " «" + t.getTitulo() + "» en tu propio backlog con urgencia propia "
						+ t.getUrgenciaPropia() + " y urgencia calculada " + t.getUrgenciaCalculada() + ".");
			} else if (e.esPeticion) {
				descripcion.add("La petición #" + t.getId() + " «" + t.getTitulo() + "» ya existía (asignada a "
						+ t.getResponsable().getNombre() + ", estado " + t.getEstado() + "); no la he duplicado."
						+ subidaTexto(t, r));
			} else {
				descripcion.add("La tarea #" + t.getId() + " «" + t.getTitulo() + "» ya estaba en tu backlog; no la he duplicado."
						+ subidaTexto(t, r));
			}
			if (e.asignacion != null) {
				descripcion.add(e.asignacion.creado()
						? "He creado el proyecto «" + e.asignacion.proyecto().getNombre() + "» (tú como responsable) y he añadido la tarea #" + t.getId() + "."
						: "La tarea #" + t.getId() + " queda dentro del proyecto «" + e.asignacion.proyecto().getNombre() + "».");
			}
			if (e.padre != null) {
				descripcion.add("He enlazado #" + t.getId() + " como subtarea que bloquea tu tarea #" + e.padre.getId() + " «"
						+ e.padre.getTitulo() + "», que tiene ahora urgencia calculada " + e.padre.getUrgenciaCalculada() + ".");
			}
		}
		for (AsignacionService.ResultadoEnlace r : enlacesExplicitos) {
			descripcion.add("Dependencia creada: #" + r.bloqueante().getId() + " «" + r.bloqueante().getTitulo() + "» ("
					+ r.bloqueante().getResponsable().getNombre() + ") bloquea a #" + r.bloqueada().getId() + " «"
					+ r.bloqueada().getTitulo() + "». La bloqueante tiene ahora urgencia calculada "
					+ r.bloqueante().getUrgenciaCalculada() + ".");
		}
		descripcion.addAll(cambiosProyecto.acciones());
		descripcion.addAll(cambiosTarea.acciones());
		for (MemoriaDto m : memorias) {
			descripcion.add("Guardado en memoria [" + m.tipo().name().toLowerCase(Locale.ROOT) + "]: " + m.contenido());
		}

		return new Acciones(creadas.stream().map(DtoMapper::tarea).toList(),
				actualizadas.stream().map(DtoMapper::tarea).toList(),
				proyectosTocados.stream().map(DtoMapper::proyecto).toList(),
				memorias, descripcion, avisos,
				estadoProyectos(empleado, extraccion.consultasProyecto(), proyectosTocados, avisos));
	}

	/**
	 * Estado detallado de los proyectos por los que pregunta el usuario ("*" = los suyos) y de los que ha modificado,
	 * para que la respuesta pueda contestar "¿cómo va X?" y confirmar cambios con datos actualizados.
	 */
	private String estadoProyectos(Empleado usuario, List<String> consultas, Set<Proyecto> tocados, List<String> avisos) {
		LinkedHashSet<Proyecto> proyectos = new LinkedHashSet<>();
		for (String c : consultas) {
			String n = Texto.normalizar(c);
			if (c.trim().equals("*") || n.equals("mis proyectos") || n.equals("todos")) {
				proyectoService.deEmpleado(usuario.getId()).stream().filter(p -> p.getEstado().activo())
						.sorted(Comparator.comparingInt(Proyecto::getPrioridad).reversed()).forEach(proyectos::add);
				continue;
			}
			proyectoService.buscarPorNombre(c).ifPresentOrElse(proyectos::add,
					() -> avisos.add("No encuentro ningún proyecto llamado «" + c + "»."));
		}
		proyectos.addAll(tocados);
		return String.join("\n", proyectos.stream().limit(MAX_PROYECTOS_DETALLADOS)
				.map(p -> proyectoService.resumenTexto(p.getId())).toList());
	}

	private static void anadirSiFalta(List<Tarea> lista, Tarea t, List<Tarea> excluir) {
		if (!lista.contains(t) && !excluir.contains(t)) lista.add(t);
	}

	/** Si la referencia es exactamente el título u objeto de algo de este mensaje, se traduce a "#id". */
	private static String referencia(String ref, Map<String, Tarea> porReferencia) {
		Tarea t = porReferencia.get(Texto.normalizar(ref));
		return t != null ? "#" + t.getId() : ref;
	}

	private static String subidaTexto(Tarea t, AsignacionService.Resultado r) {
		return r.urgenciaAnterior() == null ? ""
				: " He subido su urgencia propia de " + r.urgenciaAnterior() + " a " + t.getUrgenciaPropia()
				+ " (urgencia calculada " + t.getUrgenciaCalculada() + ").";
	}

	private String respuestaPlantilla(ContextoChat ctx, Acciones acciones) {
		String nombre = ctx.empleado().nombre().split(" ")[0];
		StringBuilder sb = new StringBuilder();
		boolean hayEstado = !acciones.estadoProyectos().isBlank();
		if (acciones.descripcion().isEmpty() && acciones.avisos().isEmpty() && !hayEstado) {
			sb.append("Entendido, ").append(nombre).append(". ");
			ctx.memoriasRelevantes().stream().filter(m -> m.tipo() == TipoMemoria.PROYECTO_ACTUAL).findFirst()
					.ifPresent(m -> sb.append("Recuerdo que ").append(minusculaInicial(m.contenido())).append(' '));
			if (!ctx.tareasAbiertas().isEmpty()) {
				TareaDto top = ctx.tareasAbiertas().getFirst();
				sb.append("Tu tarea más prioritaria ahora es #").append(top.id()).append(" «").append(top.titulo())
						.append("» (urgencia calculada ").append(top.urgenciaCalculada()).append("). ");
			}
			sb.append("Puedo pedir cosas a otros equipos, apuntarte tareas o decirte cómo va un proyecto (p. ej. «¿cómo va Orión?»).");
			return sb.toString();
		}
		if (!acciones.descripcion().isEmpty() || !acciones.avisos().isEmpty()) {
			sb.append("Hecho, ").append(nombre).append(":\n");
			acciones.descripcion().forEach(a -> sb.append("• ").append(a).append('\n'));
			acciones.avisos().forEach(a -> sb.append("⚠ ").append(a).append('\n'));
		}
		if (hayEstado) {
			sb.append(sb.isEmpty() ? "" : "\n").append(acciones.estadoProyectos());
		}
		return sb.toString().trim();
	}

	private static String minusculaInicial(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
	}

	/** Una línea por proyecto, con su relación con el usuario, para que Gemini reconozca proyectos y roles. */
	private List<String> lineasProyectos(Long empleadoId) {
		Set<Long> mios = new HashSet<>();
		proyectoService.deEmpleado(empleadoId).forEach(p -> mios.add(p.getId()));
		return proyectoService.todos().stream().map(p -> p.getNombre() + ": " + p.getEstado() + ", prioridad " + p.getPrioridad()
				+ ", responsable " + p.getResponsable().getNombre()
				+ (p.getResponsable().getId().equals(empleadoId) ? " (eres tú)" : mios.contains(p.getId()) ? " (participas)" : "")).toList();
	}

	private List<TareaDto> tareasAbiertas(Long empleadoId) {
		return tareaRepository.findByResponsableIdAndEstadoNotOrderByUrgenciaCalculadaDescFechaCreacionAsc(empleadoId, EstadoTarea.HECHA)
				.stream().limit(TAREAS_EN_CONTEXTO).map(DtoMapper::tarea).toList();
	}

	private List<TareaDto> peticionesAbiertas(Long empleadoId) {
		return tareaRepository.findBySolicitanteIdAndEstadoNot(empleadoId, EstadoTarea.HECHA)
				.stream().map(DtoMapper::tarea).toList();
	}

	private List<MensajeChatDto> historialReciente(Long empleadoId) {
		List<MensajeChatDto> ultimos = new ArrayList<>(chatRepository
				.findByEmpleadoIdOrderByFechaHoraDesc(empleadoId, PageRequest.of(0, HISTORIAL))
				.stream().map(DtoMapper::mensaje).toList());
		Collections.reverse(ultimos);
		return ultimos;
	}

	public List<MensajeChatDto> historial(Long empleadoId) {
		return tx.execute(s -> {
			empleadoService.buscar(empleadoId);
			return chatRepository.findByEmpleadoIdOrderByFechaHoraAsc(empleadoId).stream().map(DtoMapper::mensaje).toList();
		});
	}

	public EstadoIaDto estadoIa() {
		return new EstadoIaDto(gemini.disponible(), gemini.modelo());
	}
}
