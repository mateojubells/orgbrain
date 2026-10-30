package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.CambioProyectoDto;
import com.orgbrain.dto.ChatDtos.CambioTareaDto;
import com.orgbrain.dto.ProyectoDtos.*;
import com.orgbrain.entity.*;
import com.orgbrain.exception.PeticionInvalidaException;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.EmpleadoRepository;
import com.orgbrain.repository.HitoRepository;
import com.orgbrain.repository.ProyectoNotaRepository;
import com.orgbrain.repository.TareaRepository;
import com.orgbrain.util.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ejecuta los cambios de proyectos y tareas que el usuario pide al asistente.
 * Cada cambio pasa por {@link ProyectoService}, así queda en el diario del proyecto con el usuario como autor.
 * Los errores de un cambio (proyecto o persona no encontrados, datos inválidos) se devuelven como avisos
 * y no impiden aplicar el resto.
 */
@Service
@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
public class AccionesProyectoService {

	private static final Pattern NUMERO = Pattern.compile("\\d+");
	private static final Pattern REFERENCIA_ID = Pattern.compile("#\\s*(\\d+)");
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ProyectoService proyectoService;
	private final EmpleadoRepository empleadoRepository;
	private final TareaRepository tareaRepository;
	private final HitoRepository hitoRepository;
	private final ProyectoNotaRepository notaRepository;

	public AccionesProyectoService(ProyectoService proyectoService, EmpleadoRepository empleadoRepository,
			TareaRepository tareaRepository, HitoRepository hitoRepository, ProyectoNotaRepository notaRepository) {
		this.proyectoService = proyectoService;
		this.empleadoRepository = empleadoRepository;
		this.tareaRepository = tareaRepository;
		this.hitoRepository = hitoRepository;
		this.notaRepository = notaRepository;
	}

	public record Resultado(List<String> acciones, List<String> avisos, Set<Proyecto> proyectos, List<Tarea> tareas) {

		static Resultado vacio() {
			return new Resultado(new ArrayList<>(), new ArrayList<>(), new LinkedHashSet<>(), new ArrayList<>());
		}
	}

	// ---------------------------------------------------------------- Proyectos

	public Resultado aplicarCambiosProyecto(Empleado usuario, List<CambioProyectoDto> cambios, Collection<Tarea> tareasDelMensaje) {
		Resultado r = Resultado.vacio();
		for (CambioProyectoDto c : cambios) {
			String accion = c.accion() == null ? "" : c.accion().trim().toLowerCase(Locale.ROOT);
			try {
				if (accion.equals("crear_proyecto")) {
					crearProyecto(usuario, c, r);
					continue;
				}
				Optional<Proyecto> encontrado = proyectoService.buscarPorNombre(c.proyecto());
				if (encontrado.isEmpty()) {
					r.avisos().add("No encuentro el proyecto «" + c.proyecto() + "». Si es nuevo, pídeme que lo cree.");
					continue;
				}
				Proyecto p = encontrado.get();
				String descripcion = switch (accion) {
					case "cambiar_estado" -> cambiarEstado(usuario, p, c);
					case "cambiar_prioridad" -> cambiarPrioridad(usuario, p, c);
					case "cambiar_fecha_objetivo" -> cambiarFecha(usuario, p, c);
					case "cambiar_responsable" -> cambiarResponsable(usuario, p, c);
					case "anadir_miembro" -> anadirMiembro(usuario, p, c);
					case "quitar_miembro" -> quitarMiembro(usuario, p, c);
					case "crear_hito" -> crearHito(usuario, p, c);
					case "completar_hito" -> completarHito(usuario, p, c);
					case "registrar_riesgo" -> registrarNota(usuario, p, TipoNotaProyecto.RIESGO, c);
					case "registrar_decision" -> registrarNota(usuario, p, TipoNotaProyecto.DECISION, c);
					case "registrar_bloqueo" -> registrarNota(usuario, p, TipoNotaProyecto.BLOQUEO, c);
					case "registrar_nota" -> registrarNota(usuario, p, TipoNotaProyecto.NOTA, c);
					case "resolver_riesgo" -> resolverRiesgo(usuario, p, c);
					case "asignar_tarea" -> asignarTarea(usuario, p, c, tareasDelMensaje, r);
					default -> throw new PeticionInvalidaException("No sé hacer la acción «" + c.accion() + "» sobre proyectos.");
				};
				if (descripcion != null) {
					r.acciones().add(descripcion);
					r.proyectos().add(p);
				}
			} catch (PeticionInvalidaException | RecursoNoEncontradoException e) {
				r.avisos().add(e.getMessage());
			}
		}
		return r;
	}

	private void crearProyecto(Empleado usuario, CambioProyectoDto c, Resultado r) {
		if (c.proyecto() == null || c.proyecto().isBlank()) {
			throw new PeticionInvalidaException("Para crear un proyecto necesito su nombre.");
		}
		Optional<Proyecto> existente = proyectoService.buscarPorNombre(c.proyecto());
		if (existente.isPresent()) {
			r.avisos().add("Ya existe el proyecto «" + existente.get().getNombre() + "»; no lo he duplicado.");
			r.proyectos().add(existente.get());
			return;
		}
		Empleado responsable = c.persona() != null ? persona(c.persona(), usuario) : usuario;
		Integer prioridad = numero(c.valor()).filter(n -> n >= 1 && n <= 5).orElse(3);
		String objetivo = c.valor() != null && numero(c.valor()).isEmpty() ? c.valor() : null;
		Proyecto p = proyectoService.crear(new CrearProyectoRequest(limpiarNombre(c.proyecto()), null, objetivo,
				responsable.getId(), prioridad, EstadoProyecto.EN_CURSO, null, fecha(c.fecha())), usuario.getId(), OrigenTarea.CHATBOT);
		if (!responsable.getId().equals(usuario.getId())) {
			proyectoService.guardarMiembro(p.getId(), new MiembroRequest(usuario.getId(), null, null), usuario.getId());
		}
		r.acciones().add("Proyecto «" + p.getNombre() + "» creado con " + responsable.getNombre() + " como responsable, prioridad "
				+ p.getPrioridad() + (p.getFechaObjetivo() != null ? " y fecha objetivo " + FECHA.format(p.getFechaObjetivo()) : "") + ".");
		r.proyectos().add(p);
	}

	private String cambiarEstado(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		EstadoProyecto estado = estadoProyecto(c.valor());
		return aplicar(usuario, p, new ActualizarProyectoRequest(null, null, null, null, null, estado, null, null));
	}

	private String cambiarPrioridad(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		int prioridad = numero(c.valor()).filter(n -> n >= 1 && n <= 5)
				.orElseThrow(() -> new PeticionInvalidaException("La prioridad de un proyecto va de 1 a 5."));
		return aplicar(usuario, p, new ActualizarProyectoRequest(null, null, null, null, prioridad, null, null, null));
	}

	private String cambiarFecha(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		LocalDate fecha = Optional.ofNullable(fecha(c.fecha())).or(() -> Optional.ofNullable(fecha(c.valor())))
				.orElseThrow(() -> new PeticionInvalidaException("No he entendido la nueva fecha objetivo de " + p.getNombre() + "."));
		return aplicar(usuario, p, new ActualizarProyectoRequest(null, null, null, null, null, null, null, fecha));
	}

	private String cambiarResponsable(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		Empleado nuevo = persona(Optional.ofNullable(c.persona()).orElse(c.valor()), usuario);
		return aplicar(usuario, p, new ActualizarProyectoRequest(null, null, null, nuevo.getId(), null, null, null, null));
	}

	private String aplicar(Empleado usuario, Proyecto p, ActualizarProyectoRequest req) {
		List<String> cambios = proyectoService.actualizar(p.getId(), req, usuario.getId());
		return cambios.isEmpty() ? null : "Proyecto " + p.getNombre() + ": " + String.join("; ", cambios) + ".";
	}

	private String anadirMiembro(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		Empleado e = persona(Optional.ofNullable(c.persona()).orElse(c.valor()), usuario);
		Integer dedicacion = numero(c.valor()).filter(n -> n > 0 && n <= 100).orElse(null);
		ProyectoMiembro m = proyectoService.guardarMiembro(p.getId(), new MiembroRequest(e.getId(), c.rol(), dedicacion), usuario.getId());
		return e.getNombre() + " forma parte del equipo de " + p.getNombre() + " como " + m.getRolProyecto()
				+ " (" + m.getDedicacion() + "% de dedicación).";
	}

	private String quitarMiembro(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		Empleado e = persona(Optional.ofNullable(c.persona()).orElse(c.valor()), usuario);
		proyectoService.quitarMiembro(p.getId(), e.getId(), usuario.getId());
		return e.getNombre() + " ya no forma parte del equipo de " + p.getNombre() + ".";
	}

	private String crearHito(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		if (c.valor() == null || c.valor().isBlank()) {
			throw new PeticionInvalidaException("Para crear un hito en " + p.getNombre() + " necesito su nombre.");
		}
		Hito h = proyectoService.crearHito(p.getId(), new HitoRequest(c.valor().trim(), null, fecha(c.fecha())), usuario.getId());
		return "Nuevo hito en " + p.getNombre() + ": «" + h.getNombre() + "»"
				+ (h.getFechaObjetivo() != null ? " para el " + FECHA.format(h.getFechaObjetivo()) : "") + ".";
	}

	private String completarHito(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		List<Hito> pendientes = hitoRepository.findByProyectoIdOrderByFechaObjetivoAscIdAsc(p.getId()).stream()
				.filter(h -> !h.isCompletado()).toList();
		Hito hito = mejorCoincidencia(c.valor(), pendientes, Hito::getNombre)
				.orElseThrow(() -> new PeticionInvalidaException("No encuentro en " + p.getNombre() + " un hito pendiente que encaje con «" + c.valor() + "»."));
		proyectoService.actualizarHito(hito.getId(), new ActualizarHitoRequest(null, null, null, true), usuario.getId());
		return "Hito «" + hito.getNombre() + "» de " + p.getNombre() + " marcado como completado.";
	}

	private String registrarNota(Empleado usuario, Proyecto p, TipoNotaProyecto tipo, CambioProyectoDto c) {
		if (c.valor() == null || c.valor().isBlank()) {
			throw new PeticionInvalidaException("¿Qué quieres registrar en el diario de " + p.getNombre() + "?");
		}
		proyectoService.crearNota(p.getId(), new NotaRequest(tipo, c.valor().trim()), usuario.getId());
		return "Registrado en el diario de " + p.getNombre() + " [" + tipo.name().toLowerCase(Locale.ROOT) + "]: " + c.valor().trim();
	}

	private String resolverRiesgo(Empleado usuario, Proyecto p, CambioProyectoDto c) {
		List<ProyectoNota> abiertas = notaRepository.findByProyectoIdOrderByFechaDesc(p.getId()).stream()
				.filter(ProyectoNota::isAbierta).toList();
		Optional<ProyectoNota> nota = abiertas.size() == 1 && (c.valor() == null || c.valor().isBlank())
				? Optional.of(abiertas.getFirst())
				: mejorCoincidencia(c.valor(), abiertas, ProyectoNota::getContenido);
		ProyectoNota n = nota.orElseThrow(() -> new PeticionInvalidaException(
				"No encuentro en " + p.getNombre() + " un riesgo o bloqueo abierto que encaje con «" + c.valor() + "»."));
		proyectoService.actualizarNota(n.getId(), new ActualizarNotaRequest(false), usuario.getId());
		return "Marcado como resuelto en " + p.getNombre() + ": " + n.getContenido();
	}

	private String asignarTarea(Empleado usuario, Proyecto p, CambioProyectoDto c, Collection<Tarea> tareasDelMensaje, Resultado r) {
		Tarea t = tarea(c.tarea() != null ? c.tarea() : c.valor(), usuario, tareasDelMensaje);
		if (p.equals(t.getProyecto())) {
			return null;
		}
		String anterior = t.getProyecto() == null ? "" : " (antes en " + t.getProyecto().getNombre() + ")";
		t.setProyecto(p);
		proyectoService.registrar(p, usuario.getId(), TipoNotaProyecto.CAMBIO, "Tarea #" + t.getId() + " «" + t.getTitulo() + "» añadida al proyecto.");
		r.tareas().add(t);
		return "La tarea #" + t.getId() + " «" + t.getTitulo() + "» pasa al proyecto " + p.getNombre() + anterior + ".";
	}

	// ---------------------------------------------------------------- Tareas

	public Resultado aplicarCambiosTarea(Empleado usuario, List<CambioTareaDto> cambios, Collection<Tarea> tareasDelMensaje) {
		Resultado r = Resultado.vacio();
		for (CambioTareaDto c : cambios) {
			try {
				Tarea t = tarea(c.tarea(), usuario, tareasDelMensaje);
				List<String> hechos = new ArrayList<>();
				if (c.estado() != null) {
					EstadoTarea estado = estadoTarea(c.estado());
					if (estado != t.getEstado()) {
						t.setEstado(estado);
						hechos.add("estado " + estado);
					}
				}
				if (c.urgencia() != null) {
					int u = Math.max(1, Math.min(5, c.urgencia()));
					if (u != t.getUrgenciaPropia()) {
						hechos.add("urgencia propia " + t.getUrgenciaPropia() + " → " + u);
						t.setUrgenciaPropia(u);
					}
				}
				LocalDate limite = fecha(c.fechaLimite());
				if (limite != null && !limite.equals(t.getFechaLimite())) {
					t.setFechaLimite(limite);
					hechos.add("fecha límite " + FECHA.format(limite));
				}
				if (c.responsable() != null) {
					Empleado nuevo = persona(c.responsable(), usuario);
					if (!nuevo.getId().equals(t.getResponsable().getId())) {
						hechos.add("responsable " + t.getResponsable().getNombre() + " → " + nuevo.getNombre());
						t.setResponsable(nuevo);
					}
				}
				if (hechos.isEmpty()) {
					continue;
				}
				String texto = "Tarea #" + t.getId() + " «" + t.getTitulo() + "»: " + String.join(", ", hechos) + ".";
				r.acciones().add(texto);
				r.tareas().add(t);
				if (t.getProyecto() != null) {
					proyectoService.registrar(t.getProyecto(), usuario.getId(), TipoNotaProyecto.CAMBIO, texto);
					r.proyectos().add(t.getProyecto());
				}
			} catch (PeticionInvalidaException | RecursoNoEncontradoException e) {
				r.avisos().add(e.getMessage());
			}
		}
		return r;
	}

	/**
	 * Resuelve "#id" o un título. Por número vale cualquier tarea; por título solo se busca entre las tareas del
	 * mensaje, las del usuario y las peticiones que ha hecho, para no tocar por error trabajo ajeno.
	 */
	Tarea tarea(String referencia, Empleado usuario, Collection<Tarea> tareasDelMensaje) {
		if (referencia == null || referencia.isBlank()) {
			throw new PeticionInvalidaException("No sé a qué tarea te refieres; dime su número (#id).");
		}
		Matcher m = REFERENCIA_ID.matcher(referencia);
		String numero = m.find() ? m.group(1) : referencia.trim().matches("\\d+") ? referencia.trim() : null;
		if (numero != null) {
			long id = Long.parseLong(numero);
			return tareaRepository.findById(id)
					.orElseThrow(() -> new PeticionInvalidaException("No existe la tarea #" + id + "."));
		}
		List<Tarea> candidatas = new ArrayList<>(tareasDelMensaje);
		tareaRepository.findByResponsableIdAndEstadoNotOrderByUrgenciaCalculadaDescFechaCreacionAsc(usuario.getId(), EstadoTarea.HECHA)
				.stream().filter(t -> !candidatas.contains(t)).forEach(candidatas::add);
		tareaRepository.findBySolicitanteIdAndEstadoNot(usuario.getId(), EstadoTarea.HECHA)
				.stream().filter(t -> !candidatas.contains(t)).forEach(candidatas::add);
		return mejorCoincidencia(referencia, candidatas, Tarea::getTitulo)
				.orElseThrow(() -> new PeticionInvalidaException("No identifico la tarea «" + referencia + "»; dime su número (#id)."));
	}

	// ---------------------------------------------------------------- Personas, fechas y estados

	/**
	 * Busca a una persona por nombre: todas las palabras dichas tienen que aparecer en su nombre.
	 * "yo" o "mí" es el propio usuario. Si hay varias posibles, se pide concretar.
	 */
	Empleado persona(String nombre, Empleado usuario) {
		if (nombre == null || nombre.isBlank()) {
			throw new PeticionInvalidaException("¿A qué persona te refieres?");
		}
		String n = Texto.normalizar(nombre);
		if (Set.of("yo", "mi", "me", "a mi").contains(n)) {
			return usuario;
		}
		Set<String> palabras = new LinkedHashSet<>(Arrays.asList(n.split(" ")));
		palabras.removeIf(p -> p.length() < 2 || Set.of("de", "del", "la", "el", "a").contains(p));
		List<Empleado> candidatos = empleadoRepository.findAll().stream().filter(e -> {
			List<String> partes = Arrays.asList(Texto.normalizar(e.getNombre()).split(" "));
			return palabras.stream().allMatch(partes::contains);
		}).toList();
		if (candidatos.size() == 1) {
			return candidatos.getFirst();
		}
		if (candidatos.isEmpty()) {
			throw new PeticionInvalidaException("No encuentro a nadie llamado «" + nombre + "» en la empresa.");
		}
		throw new PeticionInvalidaException("Hay varias personas que encajan con «" + nombre + "»: "
				+ String.join(", ", candidatos.stream().limit(4).map(Empleado::getNombre).toList()) + ". Dime el nombre completo.");
	}

	static LocalDate fecha(String texto) {
		if (texto == null || texto.isBlank()) return null;
		String t = texto.trim();
		try {
			return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
		} catch (DateTimeParseException e) {
			try {
				return LocalDate.parse(t, FECHA);
			} catch (DateTimeParseException e2) {
				return null;
			}
		}
	}

	static EstadoProyecto estadoProyecto(String texto) {
		String t = Texto.normalizar(texto);
		if (t.contains("riesgo")) return EstadoProyecto.EN_RIESGO;
		if (t.contains("paus") || t.contains("parad") || t.contains("congel")) return EstadoProyecto.PAUSADO;
		if (t.contains("cancel") || t.contains("descart")) return EstadoProyecto.CANCELADO;
		if (t.contains("complet") || t.contains("termin") || t.contains("cerrad") || t.contains("finaliz")) return EstadoProyecto.COMPLETADO;
		if (t.contains("planific")) return EstadoProyecto.PLANIFICACION;
		if (t.contains("curso") || t.contains("activ") || t.contains("reanud") || t.contains("marcha")) return EstadoProyecto.EN_CURSO;
		throw new PeticionInvalidaException("No reconozco el estado «" + texto + "». Puede ser: planificación, en curso, en riesgo, pausado, completado o cancelado.");
	}

	static EstadoTarea estadoTarea(String texto) {
		String t = Texto.normalizar(texto);
		if (t.contains("hech") || t.contains("termin") || t.contains("complet") || t.contains("finaliz") || t.contains("cerrad")) return EstadoTarea.HECHA;
		if (t.contains("curso") || t.contains("empez") || t.contains("progres") || t.contains("trabajando")) return EstadoTarea.EN_CURSO;
		if (t.contains("pendiente") || t.contains("reabr")) return EstadoTarea.PENDIENTE;
		throw new PeticionInvalidaException("No reconozco el estado de tarea «" + texto + "» (pendiente, en curso o hecha).");
	}

	private static Optional<Integer> numero(String texto) {
		if (texto == null) return Optional.empty();
		Matcher m = NUMERO.matcher(texto);
		return m.find() ? Optional.of(Integer.parseInt(m.group())) : Optional.empty();
	}

	private static String limpiarNombre(String nombre) {
		String n = nombre.trim().replaceFirst("(?i)^(el\\s+)?proyecto:?\\s+", "");
		return n.isEmpty() ? nombre.trim() : n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
	}

	private static <T> Optional<T> mejorCoincidencia(String pista, List<T> candidatos, java.util.function.Function<T, String> texto) {
		Set<String> tokens = Texto.tokens(pista);
		if (tokens.isEmpty()) return Optional.empty();
		T mejor = null;
		double mejorRatio = 0;
		for (T c : candidatos) {
			Set<String> tc = Texto.tokens(texto.apply(c));
			long comunes = tokens.stream().filter(x -> tc.stream().anyMatch(y -> Texto.coincide(x, y))).count();
			double ratio = (double) comunes / tokens.size();
			if (ratio > mejorRatio) {
				mejor = c;
				mejorRatio = ratio;
			}
		}
		return mejorRatio >= 0.6 ? Optional.of(mejor) : Optional.empty();
	}
}
