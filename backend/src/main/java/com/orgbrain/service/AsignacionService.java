package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.PeticionExtraidaDto;
import com.orgbrain.dto.ChatDtos.TareaPropiaExtraidaDto;
import com.orgbrain.dto.ProyectoDtos.CrearProyectoRequest;
import com.orgbrain.entity.*;
import com.orgbrain.exception.PeticionInvalidaException;
import com.orgbrain.repository.DependenciaRepository;
import com.orgbrain.repository.DominioResponsabilidadRepository;
import com.orgbrain.repository.TareaRepository;
import com.orgbrain.util.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asignación automática y enlaces del chat.
 * <ul>
 *   <li>Peticiones: decide quién las gestiona según el directorio y crea la tarea en su backlog.</li>
 *   <li>Tareas propias: las crea en el backlog del usuario.</li>
 *   <li>Si ya existían (el usuario vuelve a hablar de ellas) no se duplican: se reutilizan y sube su urgencia.</li>
 *   <li>Enlaces: las dependencias explícitas que expresa el usuario y, después, subtareas y proyecto.</li>
 * </ul>
 * La creación y el enlace van por separado para que {@link ChatService} aplique primero las dependencias explícitas
 * y solo enlace como subtarea lo que no tiene ya un sitio más concreto en la cadena. La pertenencia a un proyecto
 * es un dato de la tarea ({@link Tarea#getProyecto()}), no una dependencia.
 */
@Service
@Transactional(noRollbackFor = {PeticionInvalidaException.class, com.orgbrain.exception.RecursoNoEncontradoException.class})
public class AsignacionService {

	private static final Pattern REFERENCIA_ID = Pattern.compile("#\\s*(\\d+)|^\\s*(\\d+)\\s*$");
	private static final double UMBRAL_REFERENCIA = 0.6;
	private static final double UMBRAL_TAREA_DUPLICADA = 0.75;

	private final DominioResponsabilidadRepository dominioRepository;
	private final TareaRepository tareaRepository;
	private final DependenciaRepository dependenciaRepository;
	private final DependenciaService dependenciaService;
	private final ProyectoService proyectoService;

	public AsignacionService(DominioResponsabilidadRepository dominioRepository, TareaRepository tareaRepository,
			DependenciaRepository dependenciaRepository, DependenciaService dependenciaService,
			ProyectoService proyectoService) {
		this.dominioRepository = dominioRepository;
		this.tareaRepository = tareaRepository;
		this.dependenciaRepository = dependenciaRepository;
		this.dependenciaService = dependenciaService;
		this.proyectoService = proyectoService;
	}

	/**
	 * Resultado de procesar una petición o tarea propia.
	 *
	 * @param tarea            la tarea creada o, si ya existía, la existente (null si no hay responsable)
	 * @param creada           false si se reutilizó una tarea existente
	 * @param urgenciaAnterior urgencia propia previa si se ha subido (solo en tareas existentes)
	 */
	public record Resultado(Tarea tarea, boolean creada, DominioResponsabilidad dominio, Integer urgenciaAnterior,
			String aviso) {
	}

	/** Proyecto al que se ha asociado una tarea. */
	public record AsignacionProyecto(Proyecto proyecto, boolean creado) {
	}

	/** Resultado de enlazar dos tareas mencionadas por el usuario. */
	public record ResultadoEnlace(Tarea bloqueante, Tarea bloqueada, boolean creado, String aviso) {
	}

	// ---------------------------------------------------------------- Peticiones a otras personas

	public Resultado crearTareaParaPeticion(Empleado solicitante, PeticionExtraidaDto peticion, String mensajeOriginal) {
		Optional<DominioResponsabilidad> dominio = resolverDominio(peticion);
		if (dominio.isEmpty()) {
			return new Resultado(null, false, null, null,
					"No encontré en el directorio a nadie que gestione «" + peticion.objeto() + "»");
		}
		int urgencia = urgencia(peticion.urgenciaEstimada());

		Tarea existente = buscarPeticionDuplicada(solicitante, peticion.objeto());
		if (existente != null) {
			return new Resultado(existente, false, dominio.get(), subirUrgencia(existente, urgencia), null);
		}

		Empleado responsable = elegirResponsable(dominio.get(), solicitante);
		String titulo = recortar("Petición de " + nombreCorto(solicitante) + ": " + peticion.objeto(), 200);
		StringBuilder descripcion = new StringBuilder()
				.append("Petición de ").append(solicitante.getNombre()).append(" (").append(solicitante.getRol())
				.append(", ").append(solicitante.getDepartamento()).append(") creada automáticamente por el asistente.\n")
				.append("Dominio: ").append(dominio.get().getNombre()).append(".\n");
		if (peticion.proyectoRelacionado() != null) {
			descripcion.append("Proyecto: ").append(peticion.proyectoRelacionado()).append(".\n");
		}
		descripcion.append("Mensaje original: \"").append(mensajeOriginal).append("\"");

		Tarea tarea = new Tarea(titulo, descripcion.toString(), responsable, urgencia);
		tarea.setSolicitante(solicitante);
		tarea.setOrigen(OrigenTarea.CHATBOT);
		tareaRepository.save(tarea);
		return new Resultado(tarea, true, dominio.get(), null, null);
	}

	/**
	 * 1) Código de dominio propuesto por Gemini, si existe y tiene responsables.
	 * 2) Si no, el dominio con más palabras clave presentes en el objeto (+1 si coincide el tipo de petición).
	 */
	Optional<DominioResponsabilidad> resolverDominio(PeticionExtraidaDto peticion) {
		List<DominioResponsabilidad> dominios = dominioRepository.findAllByOrderByNombreAsc();
		if (peticion.codigoDominio() != null) {
			Optional<DominioResponsabilidad> propuesto = dominios.stream()
					.filter(d -> d.getCodigo().equalsIgnoreCase(peticion.codigoDominio().trim()))
					.filter(d -> !d.getResponsables().isEmpty())
					.findFirst();
			if (propuesto.isPresent()) {
				return propuesto;
			}
		}

		String objetoNormalizado = " " + Texto.normalizar(peticion.objeto()) + " ";
		Set<String> tokens = Texto.tokens(peticion.objeto());
		DominioResponsabilidad mejor = null;
		int mejorPuntos = 0;
		for (DominioResponsabilidad d : dominios) {
			if (d.getResponsables().isEmpty() || d.getPalabrasClave() == null) continue;
			int puntos = 0;
			for (String clave : d.getPalabrasClave().split(",")) {
				String c = Texto.normalizar(clave);
				if (c.isEmpty()) continue;
				boolean coincide = c.contains(" ")
						? objetoNormalizado.contains(" " + c + " ")
						: tokens.stream().anyMatch(t -> Texto.coincide(t, c));
				if (coincide) {
					puntos += 2;
				}
			}
			if (puntos > 0 && d.getTipoPeticion() == peticion.tipoPeticion()) {
				puntos += 1;
			}
			if (puntos > mejorPuntos) {
				mejor = d;
				mejorPuntos = puntos;
			}
		}
		return Optional.ofNullable(mejor);
	}

	/** Entre los responsables del dominio, el de menos tareas abiertas (evitando al propio solicitante si hay alternativa). */
	Empleado elegirResponsable(DominioResponsabilidad dominio, Empleado solicitante) {
		List<Empleado> candidatos = dominio.getResponsables().stream()
				.filter(e -> !e.getId().equals(solicitante.getId()))
				.toList();
		if (candidatos.isEmpty()) {
			candidatos = List.copyOf(dominio.getResponsables());
		}
		return candidatos.stream()
				.min(Comparator.<Empleado>comparingLong(e -> tareaRepository.countByResponsableIdAndEstadoNot(e.getId(), EstadoTarea.HECHA))
						.thenComparing(Empleado::getId))
				.orElseThrow();
	}

	/** Petición abierta del mismo solicitante cuyo título contiene todas las palabras del objeto. */
	private Tarea buscarPeticionDuplicada(Empleado solicitante, String objeto) {
		Set<String> tokens = Texto.tokens(objeto);
		if (tokens.isEmpty()) return null;
		for (Tarea t : peticionesAbiertas(solicitante)) {
			if (t.getOrigen() != OrigenTarea.CHATBOT) continue;
			if (comunes(tokens, Texto.tokens(t.getTitulo())) == tokens.size()) {
				return t;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- Tareas propias

	public Resultado crearTareaPropia(Empleado empleado, TareaPropiaExtraidaDto extraida, String mensajeOriginal) {
		int urgencia = urgencia(extraida.urgenciaEstimada());
		Tarea existente = buscarTareaPropiaSimilar(empleado, extraida.titulo());
		if (existente != null) {
			return new Resultado(existente, false, null, subirUrgencia(existente, urgencia), null);
		}
		String descripcion = extraida.descripcion() != null
				? extraida.descripcion()
				: "Creada por el asistente a partir del mensaje: \"" + mensajeOriginal + "\"";
		Tarea tarea = new Tarea(recortar(capitalizar(extraida.titulo()), 200), descripcion, empleado, urgencia);
		tarea.setOrigen(OrigenTarea.CHATBOT);
		tareaRepository.save(tarea);
		return new Resultado(tarea, true, null, null, null);
	}

	/** Tarea abierta del usuario con casi el mismo título (≥ 75% de palabras en común y al menos 2). */
	private Tarea buscarTareaPropiaSimilar(Empleado empleado, String titulo) {
		Set<String> tokens = Texto.tokens(titulo);
		if (tokens.size() < 2) return null;
		for (Tarea t : tareasAbiertas(empleado)) {
			Set<String> tituloTokens = Texto.tokens(t.getTitulo());
			long comunes = comunes(tokens, tituloTokens);
			if (comunes >= 2 && (double) comunes / Math.max(tokens.size(), tituloTokens.size()) >= UMBRAL_TAREA_DUPLICADA) {
				return t;
			}
		}
		return null;
	}

	private static Integer subirUrgencia(Tarea tarea, int urgencia) {
		if (urgencia <= tarea.getUrgenciaPropia()) {
			return null;
		}
		int anterior = tarea.getUrgenciaPropia();
		tarea.setUrgenciaPropia(urgencia);
		return anterior;
	}

	// ---------------------------------------------------------------- Dependencias expresadas por el usuario

	/**
	 * Enlaza dos tareas que el usuario ha relacionado ("sin la licencia no puedo hacer el briefing").
	 * Las referencias ("#301", un título o el objeto de una petición) solo se buscan entre las tareas del mensaje,
	 * las tareas abiertas del usuario y las peticiones abiertas que ha hecho.
	 *
	 * @param creadasEnMensaje solo estas pasan a ser subtarea de la bloqueada; una tarea que ya existía conserva su padre
	 */
	public ResultadoEnlace enlazarReferencias(Empleado usuario, String refBloqueante, String refBloqueada,
			Collection<Tarea> tareasDelMensaje, Collection<Tarea> creadasEnMensaje) {
		List<Tarea> candidatas = new ArrayList<>(tareasDelMensaje);
		tareasAbiertas(usuario).stream().filter(t -> !candidatas.contains(t)).forEach(candidatas::add);
		peticionesAbiertas(usuario).stream().filter(t -> !candidatas.contains(t)).forEach(candidatas::add);

		Tarea bloqueante = resolverReferencia(refBloqueante, candidatas);
		Tarea bloqueada = resolverReferencia(refBloqueada, candidatas);
		if (bloqueante == null || bloqueada == null) {
			String falta = bloqueante == null ? refBloqueante : refBloqueada;
			return new ResultadoEnlace(bloqueante, bloqueada, false,
					"No identifiqué la tarea «" + falta + "» para enlazarla; dime su número (#id) y la enlazo.");
		}
		if (bloqueante.getId().equals(bloqueada.getId())
				|| dependenciaRepository.existsByBloqueanteIdAndBloqueadaId(bloqueante.getId(), bloqueada.getId())) {
			return new ResultadoEnlace(bloqueante, bloqueada, false, null);
		}
		try {
			boolean creado = enlazar(bloqueante, bloqueada, creadasEnMensaje.contains(bloqueante));
			return new ResultadoEnlace(bloqueante, bloqueada, creado, null);
		} catch (PeticionInvalidaException e) {
			return new ResultadoEnlace(bloqueante, bloqueada, false, e.getMessage());
		}
	}

	private static Tarea resolverReferencia(String referencia, List<Tarea> candidatas) {
		if (referencia == null || referencia.isBlank()) return null;
		Matcher m = REFERENCIA_ID.matcher(referencia);
		if (m.find()) {
			long id = Long.parseLong(m.group(1) != null ? m.group(1) : m.group(2));
			Optional<Tarea> porId = candidatas.stream().filter(t -> t.getId() == id).findFirst();
			if (porId.isPresent()) return porId.get();
		}
		return mejorPorTitulo(referencia, candidatas);
	}

	/** Candidata cuyo título contiene más palabras de la pista (≥ 60%). A igualdad, la primera de la lista. */
	private static Tarea mejorPorTitulo(String pista, List<Tarea> candidatas) {
		Set<String> tokensPista = Texto.tokens(pista);
		if (tokensPista.isEmpty()) return null;
		Tarea mejor = null;
		double mejorRatio = 0;
		for (Tarea t : candidatas) {
			double ratio = (double) comunes(tokensPista, Texto.tokens(t.getTitulo())) / tokensPista.size();
			if (ratio > mejorRatio) {
				mejor = t;
				mejorRatio = ratio;
			}
		}
		return mejorRatio >= UMBRAL_REFERENCIA ? mejor : null;
	}

	// ---------------------------------------------------------------- Proyecto y subtareas

	/**
	 * Asocia la tarea a su proyecto (si aún no tiene) buscándolo por nombre. Si no existe y {@code crearSiFalta},
	 * crea el proyecto con el usuario como responsable. Devuelve null si no hay cambio.
	 */
	public AsignacionProyecto asignarProyecto(Empleado usuario, Tarea tarea, String nombreProyecto, boolean crearSiFalta) {
		if (nombreProyecto == null || nombreProyecto.isBlank() || tarea.getProyecto() != null) {
			return null;
		}
		Optional<Proyecto> existente = proyectoService.buscarPorNombre(nombreProyecto);
		if (existente.isPresent()) {
			tarea.setProyecto(existente.get());
			return new AsignacionProyecto(existente.get(), false);
		}
		if (!crearSiFalta) {
			return null;
		}
		String nombre = capitalizar(nombreProyecto.trim().replaceFirst("(?i)^(el\\s+)?proyecto:?\\s+", ""));
		Proyecto nuevo = proyectoService.crear(new CrearProyectoRequest(recortar(nombre, 150), null, null, usuario.getId(),
				Math.max(3, tarea.getUrgenciaPropia()), EstadoProyecto.EN_CURSO, null, null), usuario.getId(), OrigenTarea.CHATBOT);
		tarea.setProyecto(nuevo);
		return new AsignacionProyecto(nuevo, true);
	}

	/**
	 * Enlaza la tarea como subtarea que bloquea a la indicada ("#12" o un título de sus tareas abiertas), así el motor
	 * le transmite su urgencia. Hereda el proyecto del padre si no tiene. Devuelve null si no hay enlace nuevo
	 * (no encontrada, ya enlazada o crearía un ciclo).
	 */
	public Tarea enlazarComoSubtarea(Empleado usuario, Tarea tarea, String esSubtareaDe) {
		if (esSubtareaDe == null || esSubtareaDe.isBlank()) {
			return null;
		}
		List<Tarea> abiertas = new ArrayList<>(tareasAbiertas(usuario));
		abiertas.removeIf(t -> t.getId().equals(tarea.getId()));
		Tarea padre = resolverReferencia(esSubtareaDe, abiertas);
		if (padre == null) {
			return null;
		}
		try {
			if (!enlazar(tarea, padre, true)) {
				return null;
			}
		} catch (PeticionInvalidaException e) {
			return null;
		}
		if (tarea.getProyecto() == null) {
			tarea.setProyecto(padre.getProyecto());
		}
		return padre;
	}

	/**
	 * {@code tarea} pasa a bloquear a {@code bloqueada} (DURA), así el motor le transmite su urgencia.
	 * Si {@code marcarSubtarea} y no tenía padre, se marca como subtarea suya. Devuelve false si ya existía.
	 */
	private boolean enlazar(Tarea tarea, Tarea bloqueada, boolean marcarSubtarea) {
		if (bloqueada.getId().equals(tarea.getId())
				|| dependenciaRepository.existsByBloqueanteIdAndBloqueadaId(tarea.getId(), bloqueada.getId())) {
			return false;
		}
		dependenciaService.crearSinRecalcular(tarea, bloqueada, TipoDependencia.DURA);
		if (marcarSubtarea && tarea.getTareaPadre() == null) {
			tarea.setTareaPadre(bloqueada);
		}
		return true;
	}

	// ---------------------------------------------------------------- Utilidades

	private List<Tarea> tareasAbiertas(Empleado empleado) {
		return tareaRepository.findByResponsableIdAndEstadoNotOrderByUrgenciaCalculadaDescFechaCreacionAsc(
				empleado.getId(), EstadoTarea.HECHA);
	}

	private List<Tarea> peticionesAbiertas(Empleado solicitante) {
		return tareaRepository.findBySolicitanteIdAndEstadoNot(solicitante.getId(), EstadoTarea.HECHA);
	}

	private static long comunes(Set<String> a, Set<String> b) {
		return a.stream().filter(x -> b.stream().anyMatch(y -> Texto.coincide(x, y))).count();
	}

	private static int urgencia(Integer u) {
		return u == null ? 3 : Math.max(1, Math.min(5, u));
	}

	private static String nombreCorto(Empleado e) {
		String[] partes = e.getNombre().split(" ");
		return partes.length >= 2 ? partes[0] + " " + partes[1] : e.getNombre();
	}

	private static String capitalizar(String s) {
		String t = s.trim();
		return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
	}

	private static String recortar(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
