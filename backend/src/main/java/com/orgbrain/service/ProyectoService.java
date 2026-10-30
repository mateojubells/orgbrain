package com.orgbrain.service;

import com.orgbrain.dto.DtoMapper;
import com.orgbrain.dto.ProyectoDtos.*;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.*;
import com.orgbrain.exception.PeticionInvalidaException;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.*;
import com.orgbrain.util.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gestión de proyectos: CRUD, equipo, hitos, diario y el análisis que da el control del proyecto
 * (salud, progreso, prioridades, siguientes pasos, cuellos de botella y dependencias con otros proyectos).
 * Todos los cambios quedan registrados en el diario del proyecto con su autor.
 */
@Service
@Transactional(readOnly = true)
public class ProyectoService {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final int DIAS_AVISO_FECHA_OBJETIVO = 14;

	private final ProyectoRepository proyectoRepository;
	private final ProyectoMiembroRepository miembroRepository;
	private final HitoRepository hitoRepository;
	private final ProyectoNotaRepository notaRepository;
	private final TareaRepository tareaRepository;
	private final DependenciaRepository dependenciaRepository;
	private final EmpleadoRepository empleadoRepository;
	private final MotorPriorizacionService motor;

	public ProyectoService(ProyectoRepository proyectoRepository, ProyectoMiembroRepository miembroRepository,
			HitoRepository hitoRepository, ProyectoNotaRepository notaRepository, TareaRepository tareaRepository,
			DependenciaRepository dependenciaRepository, EmpleadoRepository empleadoRepository,
			MotorPriorizacionService motor) {
		this.proyectoRepository = proyectoRepository;
		this.miembroRepository = miembroRepository;
		this.hitoRepository = hitoRepository;
		this.notaRepository = notaRepository;
		this.tareaRepository = tareaRepository;
		this.dependenciaRepository = dependenciaRepository;
		this.empleadoRepository = empleadoRepository;
		this.motor = motor;
	}

	// ---------------------------------------------------------------- Consultas

	/** Todos los proyectos con sus indicadores. {@code participo} se marca respecto a {@code empleadoId}. */
	public List<ProyectoListadoDto> listar(Long empleadoId) {
		List<Proyecto> proyectos = proyectoRepository.findAllByOrderByPrioridadDescNombreAsc();
		Set<Long> mios = empleadoId == null ? Set.of()
				: proyectoRepository.findDeEmpleado(empleadoId).stream().map(Proyecto::getId).collect(Collectors.toSet());
		Map<Long, List<Tarea>> tareas = agrupar(tareaRepository.findAllByOrderByUrgenciaCalculadaDesc().stream()
				.filter(t -> t.getProyecto() != null).toList(), t -> t.getProyecto().getId());
		Map<Long, List<ProyectoMiembro>> miembros = agrupar(miembroRepository.findAll(), m -> m.getProyecto().getId());
		Map<Long, List<Hito>> hitos = agrupar(hitoRepository.findAll(), h -> h.getProyecto().getId());
		Map<Long, List<ProyectoNota>> notas = agrupar(notaRepository.findAll(), n -> n.getProyecto().getId());
		return proyectos.stream().map(p -> listado(p, tareas.getOrDefault(p.getId(), List.of()),
				miembros.getOrDefault(p.getId(), List.of()), hitos.getOrDefault(p.getId(), List.of()),
				notas.getOrDefault(p.getId(), List.of()), mios.contains(p.getId()))).toList();
	}

	public ProyectoDetalleDto detalle(Long id, Long empleadoId) {
		Proyecto p = buscar(id);
		List<Tarea> tareas = tareaRepository.findByProyectoIdOrderByUrgenciaCalculadaDesc(id);
		List<ProyectoMiembro> miembros = miembroRepository.findByProyectoId(id);
		List<Hito> hitos = hitoRepository.findByProyectoIdOrderByFechaObjetivoAscIdAsc(id);
		List<ProyectoNota> notas = notaRepository.findByProyectoIdOrderByFechaDesc(id);
		boolean participo = empleadoId != null && (p.getResponsable().getId().equals(empleadoId)
				|| miembros.stream().anyMatch(m -> m.getEmpleado().getId().equals(empleadoId))
				|| tareas.stream().anyMatch(t -> t.getResponsable().getId().equals(empleadoId)));

		Map<EstadoTarea, Long> porEstado = new EnumMap<>(EstadoTarea.class);
		for (EstadoTarea e : EstadoTarea.values()) {
			porEstado.put(e, tareas.stream().filter(t -> t.getEstado() == e).count());
		}
		List<Tarea> abiertas = tareas.stream().filter(t -> t.getEstado() != EstadoTarea.HECHA).toList();
		List<TareaDto> prioridades = abiertas.stream().limit(8).map(DtoMapper::tarea).toList();
		List<TareaDto> siguientesPasos = abiertas.stream()
				.filter(t -> t.getBloqueantesPendientes() == 0)
				.sorted(Comparator.comparing((Tarea t) -> t.getEstado() != EstadoTarea.EN_CURSO)
						.thenComparing(Comparator.comparingDouble(Tarea::getUrgenciaCalculada).reversed()))
				.limit(6).map(DtoMapper::tarea).toList();
		List<TareaDto> cuellos = abiertas.stream()
				.filter(t -> t.getTareasBloqueadas() > 0)
				.sorted(Comparator.comparingInt(Tarea::getTareasBloqueadas).reversed()
						.thenComparing(Comparator.comparingDouble(Tarea::getUrgenciaCalculada).reversed()))
				.limit(5).map(DtoMapper::tarea).toList();

		Set<Long> ids = tareas.stream().map(Tarea::getId).collect(Collectors.toSet());
		List<com.orgbrain.dto.TareaDtos.AristaGrafoDto> internas = new ArrayList<>();
		List<DependenciaExternaDto> externas = new ArrayList<>();
		for (Dependencia d : dependenciaRepository.findAllConTareas()) {
			boolean entra = ids.contains(d.getBloqueada().getId());
			boolean sale = ids.contains(d.getBloqueante().getId());
			if (entra && sale) {
				internas.add(DtoMapper.arista(d));
			} else if ((entra || sale) && d.getBloqueante().getEstado() != EstadoTarea.HECHA
					&& d.getBloqueada().getEstado() != EstadoTarea.HECHA) {
				externas.add(new DependenciaExternaDto(
						DtoMapper.tareaResumen(d.getBloqueante(), d.getTipo()), DtoMapper.proyecto(d.getBloqueante().getProyecto()),
						DtoMapper.tareaResumen(d.getBloqueada(), d.getTipo()), DtoMapper.proyecto(d.getBloqueada().getProyecto()),
						entra));
			}
		}

		return new ProyectoDetalleDto(
				listado(p, tareas, miembros, hitos, notas, participo),
				p.getDescripcion(),
				porEstado,
				tareas.stream().map(DtoMapper::tarea).toList(),
				prioridades, siguientesPasos, cuellos, externas, internas,
				equipo(p, tareas, miembros),
				hitos.stream().map(DtoMapper::hito).toList(),
				notas.stream().map(DtoMapper::nota).toList());
	}

	public Proyecto buscar(Long id) {
		return proyectoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proyecto", id));
	}

	/** Búsqueda tolerante por nombre: exacto sin acentos o, si no, el que comparte más palabras (≥ 60%). */
	public Optional<Proyecto> buscarPorNombre(String nombre) {
		if (nombre == null || nombre.isBlank()) return Optional.empty();
		String normalizado = Texto.normalizar(nombre.replaceFirst("(?i)^\\s*proyecto:?\\s+", ""));
		List<Proyecto> todos = proyectoRepository.findAll();
		Optional<Proyecto> exacto = todos.stream().filter(p -> Texto.normalizar(p.getNombre()).equals(normalizado)).findFirst();
		if (exacto.isPresent()) return exacto;
		Set<String> tokens = Texto.tokens(normalizado);
		if (tokens.isEmpty()) return Optional.empty();
		Proyecto mejor = null;
		double mejorRatio = 0;
		for (Proyecto p : todos) {
			Set<String> tp = Texto.tokens(p.getNombre());
			long comunes = tokens.stream().filter(t -> tp.stream().anyMatch(x -> Texto.coincide(t, x))).count();
			double ratio = (double) comunes / Math.max(tokens.size(), tp.size());
			if (ratio > mejorRatio) {
				mejor = p;
				mejorRatio = ratio;
			}
		}
		return mejorRatio >= 0.6 ? Optional.of(mejor) : Optional.empty();
	}

	public List<Proyecto> deEmpleado(Long empleadoId) {
		return proyectoRepository.findDeEmpleado(empleadoId);
	}

	public List<Proyecto> todos() {
		return proyectoRepository.findAllByOrderByPrioridadDescNombreAsc();
	}

	public boolean esMiembroOResponsable(Proyecto p, Long empleadoId) {
		return p.getResponsable().getId().equals(empleadoId)
				|| miembroRepository.existsByProyectoIdAndEmpleadoId(p.getId(), empleadoId);
	}

	// ---------------------------------------------------------------- Cambios

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public Proyecto crear(CrearProyectoRequest req, Long autorId, OrigenTarea origen) {
		String nombre = req.nombre().trim();
		if (proyectoRepository.findByNombreIgnoreCase(nombre).isPresent()) {
			throw new PeticionInvalidaException("Ya existe un proyecto llamado «" + nombre + "»");
		}
		Proyecto p = new Proyecto(nombre, empleado(req.responsableId()));
		p.setDescripcion(req.descripcion());
		p.setObjetivo(req.objetivo());
		p.setPrioridad(req.prioridad() == null ? 3 : req.prioridad());
		p.setEstado(req.estado() == null ? EstadoProyecto.EN_CURSO : req.estado());
		p.setFechaInicio(req.fechaInicio());
		p.setFechaObjetivo(req.fechaObjetivo());
		p.setOrigen(origen);
		proyectoRepository.save(p);
		miembroRepository.save(new ProyectoMiembro(p, p.getResponsable(), "Responsable del proyecto", 50));
		registrar(p, autorId, TipoNotaProyecto.CAMBIO, "Proyecto creado con responsable " + p.getResponsable().getNombre()
				+ ", prioridad " + p.getPrioridad() + (p.getFechaObjetivo() != null ? " y fecha objetivo " + FECHA.format(p.getFechaObjetivo()) : "") + ".");
		return p;
	}

	/** Aplica los cambios y deja constancia de cada uno en el diario. Recalcula prioridades si afectan al motor. */
	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public List<String> actualizar(Long id, ActualizarProyectoRequest req, Long autorId) {
		Proyecto p = buscar(id);
		List<String> cambios = new ArrayList<>();
		if (req.nombre() != null && !req.nombre().isBlank() && !req.nombre().trim().equals(p.getNombre())) {
			proyectoRepository.findByNombreIgnoreCase(req.nombre().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
				throw new PeticionInvalidaException("Ya existe un proyecto llamado «" + req.nombre().trim() + "»");
			});
			cambios.add("Nombre: «" + p.getNombre() + "» → «" + req.nombre().trim() + "»");
			p.setNombre(req.nombre().trim());
		}
		if (req.descripcion() != null && !req.descripcion().equals(p.getDescripcion())) {
			p.setDescripcion(req.descripcion());
			cambios.add("Descripción actualizada");
		}
		if (req.objetivo() != null && !req.objetivo().equals(p.getObjetivo())) {
			p.setObjetivo(req.objetivo());
			cambios.add("Objetivo: " + req.objetivo());
		}
		if (req.responsableId() != null && !req.responsableId().equals(p.getResponsable().getId())) {
			Empleado nuevo = empleado(req.responsableId());
			cambios.add("Responsable: " + p.getResponsable().getNombre() + " → " + nuevo.getNombre());
			p.setResponsable(nuevo);
			if (!miembroRepository.existsByProyectoIdAndEmpleadoId(id, nuevo.getId())) {
				miembroRepository.save(new ProyectoMiembro(p, nuevo, "Responsable del proyecto", 50));
			}
		}
		boolean afectaMotor = false;
		if (req.prioridad() != null && req.prioridad() != p.getPrioridad()) {
			cambios.add("Prioridad: " + p.getPrioridad() + " → " + req.prioridad());
			p.setPrioridad(req.prioridad());
			afectaMotor = true;
		}
		if (req.estado() != null && req.estado() != p.getEstado()) {
			cambios.add("Estado: " + p.getEstado() + " → " + req.estado());
			p.setEstado(req.estado());
			afectaMotor = true;
		}
		if (req.fechaInicio() != null && !req.fechaInicio().equals(p.getFechaInicio())) {
			cambios.add("Fecha de inicio: " + FECHA.format(req.fechaInicio()));
			p.setFechaInicio(req.fechaInicio());
		}
		if (req.fechaObjetivo() != null && !req.fechaObjetivo().equals(p.getFechaObjetivo())) {
			cambios.add("Fecha objetivo: " + (p.getFechaObjetivo() == null ? "sin fecha" : FECHA.format(p.getFechaObjetivo()))
					+ " → " + FECHA.format(req.fechaObjetivo()));
			p.setFechaObjetivo(req.fechaObjetivo());
		}
		if (!cambios.isEmpty()) {
			registrar(p, autorId, TipoNotaProyecto.CAMBIO, String.join(". ", cambios) + ".");
		}
		if (afectaMotor) {
			motor.recalcularTodo();
		}
		return cambios;
	}

	/** Añade a una persona al equipo o actualiza su rol y dedicación si ya estaba. */
	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public ProyectoMiembro guardarMiembro(Long proyectoId, MiembroRequest req, Long autorId) {
		Proyecto p = buscar(proyectoId);
		Empleado e = empleado(req.empleadoId());
		Optional<ProyectoMiembro> existente = miembroRepository.findByProyectoIdAndEmpleadoId(proyectoId, e.getId());
		if (existente.isPresent()) {
			ProyectoMiembro m = existente.get();
			if (req.rolProyecto() != null) m.setRolProyecto(req.rolProyecto());
			if (req.dedicacion() != null) m.setDedicacion(req.dedicacion());
			registrar(p, autorId, TipoNotaProyecto.CAMBIO, "Actualizado " + e.getNombre() + " en el equipo"
					+ (m.getRolProyecto() != null ? " (" + m.getRolProyecto() + ", " + m.getDedicacion() + "%)" : "") + ".");
			return m;
		}
		ProyectoMiembro m = miembroRepository.save(new ProyectoMiembro(p, e,
				req.rolProyecto() != null ? req.rolProyecto() : e.getRol(), req.dedicacion() != null ? req.dedicacion() : 20));
		registrar(p, autorId, TipoNotaProyecto.CAMBIO, e.getNombre() + " se incorpora al equipo como "
				+ m.getRolProyecto() + " (" + m.getDedicacion() + "%).");
		return m;
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public void quitarMiembro(Long proyectoId, Long empleadoId, Long autorId) {
		Proyecto p = buscar(proyectoId);
		if (p.getResponsable().getId().equals(empleadoId)) {
			throw new PeticionInvalidaException("No se puede quitar al responsable del proyecto; cambia antes el responsable.");
		}
		ProyectoMiembro m = miembroRepository.findByProyectoIdAndEmpleadoId(proyectoId, empleadoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Miembro del proyecto", empleadoId));
		long abiertas = tareaRepository.findByProyectoIdOrderByUrgenciaCalculadaDesc(proyectoId).stream()
				.filter(t -> t.getResponsable().getId().equals(empleadoId) && t.getEstado() != EstadoTarea.HECHA).count();
		miembroRepository.delete(m);
		registrar(p, autorId, TipoNotaProyecto.CAMBIO, m.getEmpleado().getNombre() + " sale del equipo"
				+ (abiertas > 0 ? "; aún tiene " + abiertas + " tarea(s) abierta(s) en el proyecto que habrá que reasignar." : "."));
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public Hito crearHito(Long proyectoId, HitoRequest req, Long autorId) {
		Proyecto p = buscar(proyectoId);
		Hito h = hitoRepository.save(new Hito(p, req.nombre().trim(), req.descripcion(), req.fechaObjetivo()));
		registrar(p, autorId, TipoNotaProyecto.CAMBIO, "Nuevo hito «" + h.getNombre() + "»"
				+ (h.getFechaObjetivo() != null ? " para el " + FECHA.format(h.getFechaObjetivo()) : "") + ".");
		return h;
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public Hito actualizarHito(Long hitoId, ActualizarHitoRequest req, Long autorId) {
		Hito h = hitoRepository.findById(hitoId).orElseThrow(() -> new RecursoNoEncontradoException("Hito", hitoId));
		List<String> cambios = new ArrayList<>();
		if (req.nombre() != null && !req.nombre().isBlank()) {
			h.setNombre(req.nombre().trim());
		}
		if (req.descripcion() != null) {
			h.setDescripcion(req.descripcion());
		}
		if (req.fechaObjetivo() != null && !req.fechaObjetivo().equals(h.getFechaObjetivo())) {
			cambios.add("nueva fecha " + FECHA.format(req.fechaObjetivo()));
			h.setFechaObjetivo(req.fechaObjetivo());
		}
		if (req.completado() != null && req.completado() != h.isCompletado()) {
			h.completar(req.completado());
			cambios.add(req.completado() ? "completado" : "reabierto");
		}
		if (!cambios.isEmpty()) {
			registrar(h.getProyecto(), autorId, TipoNotaProyecto.CAMBIO, "Hito «" + h.getNombre() + "»: " + String.join(", ", cambios) + ".");
		}
		return h;
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public ProyectoNota crearNota(Long proyectoId, NotaRequest req, Long autorId) {
		return registrar(buscar(proyectoId), autorId, req.tipo(), req.contenido().trim());
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public ProyectoNota actualizarNota(Long notaId, ActualizarNotaRequest req, Long autorId) {
		ProyectoNota n = notaRepository.findById(notaId).orElseThrow(() -> new RecursoNoEncontradoException("Nota", notaId));
		if (n.isAbierta() != req.abierta()) {
			n.setAbierta(req.abierta());
			registrar(n.getProyecto(), autorId, TipoNotaProyecto.CAMBIO,
					(req.abierta() ? "Reabierto" : "Resuelto") + " " + n.getTipo().name().toLowerCase(Locale.ROOT) + ": " + n.getContenido());
		}
		return n;
	}

	@Transactional(noRollbackFor = {PeticionInvalidaException.class, RecursoNoEncontradoException.class})
	public ProyectoNota registrar(Proyecto p, Long autorId, TipoNotaProyecto tipo, String contenido) {
		Empleado autor = autorId == null ? null : empleadoRepository.findById(autorId).orElse(null);
		return notaRepository.save(new ProyectoNota(p, autor, tipo, contenido));
	}

	// ---------------------------------------------------------------- Texto para el asistente

	/** Resumen denso del estado del proyecto para dárselo como contexto a Gemini (o mostrarlo sin IA). */
	public String resumenTexto(Long proyectoId) {
		ProyectoDetalleDto d = detalle(proyectoId, null);
		ProyectoListadoDto r = d.resumen();
		StringBuilder sb = new StringBuilder();
		sb.append("### Proyecto ").append(r.nombre()).append('\n');
		sb.append("- Estado ").append(r.estado()).append(", prioridad ").append(r.prioridad())
				.append(", responsable ").append(r.responsable().nombre());
		if (r.fechaObjetivo() != null) sb.append(", fecha objetivo ").append(FECHA.format(r.fechaObjetivo()));
		sb.append('\n');
		if (r.objetivo() != null) sb.append("- Objetivo: ").append(r.objetivo()).append('\n');
		sb.append("- Salud ").append(r.salud().nivel()).append(": ").append(String.join("; ", r.salud().motivos())).append('\n');
		sb.append("- Progreso ").append(r.progreso()).append("% (").append(d.tareasPorEstado().get(EstadoTarea.HECHA))
				.append(" hechas, ").append(d.tareasPorEstado().get(EstadoTarea.EN_CURSO)).append(" en curso, ")
				.append(d.tareasPorEstado().get(EstadoTarea.PENDIENTE)).append(" pendientes; ")
				.append(r.tareasBloqueadas()).append(" esperando a otras, ").append(r.tareasVencidas()).append(" vencidas)\n");
		if (r.proximoHito() != null) {
			sb.append("- Próximo hito: ").append(r.proximoHito().nombre());
			if (r.proximoHito().fechaObjetivo() != null) sb.append(" (").append(FECHA.format(r.proximoHito().fechaObjetivo())).append(')');
			sb.append('\n');
		}
		lista(sb, "Prioridades", d.prioridades().stream().limit(5).map(ProyectoService::tareaCorta).toList());
		lista(sb, "Siguientes pasos (sin bloqueos)", d.siguientesPasos().stream().map(ProyectoService::tareaCorta).toList());
		lista(sb, "Cuellos de botella", d.cuellosDeBotella().stream()
				.map(t -> tareaCorta(t) + " bloquea a " + t.tareasBloqueadas() + " tarea(s)").toList());
		lista(sb, "Dependencias con fuera del proyecto", d.dependenciasExternas().stream().limit(6)
				.map(x -> "#" + x.bloqueante().id() + " «" + x.bloqueante().titulo() + "» (" + x.bloqueante().responsable().nombre()
						+ (x.proyectoBloqueante() != null ? ", " + x.proyectoBloqueante().nombre() : "") + ") bloquea a #"
						+ x.bloqueada().id() + " «" + x.bloqueada().titulo() + "»").toList());
		lista(sb, "Equipo", d.equipo().stream()
				.map(m -> m.empleado().nombre() + (m.esResponsable() ? " (responsable)" : m.rolProyecto() != null ? " (" + m.rolProyecto() + ")" : "")
						+ ": " + m.tareasAbiertas() + " abiertas en el proyecto, " + m.cargaTotalAbierta() + " en total").toList());
		lista(sb, "Riesgos y bloqueos abiertos", d.notas().stream()
				.filter(n -> n.abierta()).map(n -> "[" + n.tipo() + "] " + n.contenido()).toList());
		lista(sb, "Decisiones recientes", d.notas().stream()
				.filter(n -> n.tipo() == TipoNotaProyecto.DECISION).limit(3).map(NotaDto::contenido).toList());
		return sb.toString();
	}

	private static String tareaCorta(TareaDto t) {
		return "#" + t.id() + " «" + t.titulo() + "» (" + t.responsable().nombre() + ", " + t.estado()
				+ ", urgencia " + t.urgenciaCalculada() + ")";
	}

	private static void lista(StringBuilder sb, String titulo, List<String> elementos) {
		if (elementos.isEmpty()) return;
		sb.append("- ").append(titulo).append(":\n");
		elementos.forEach(e -> sb.append("  - ").append(e).append('\n'));
	}

	// ---------------------------------------------------------------- Cálculos

	private ProyectoListadoDto listado(Proyecto p, List<Tarea> tareas, List<ProyectoMiembro> miembros, List<Hito> hitos,
			List<ProyectoNota> notas, boolean participo) {
		LocalDate hoy = LocalDate.now();
		int total = tareas.size();
		int hechas = (int) tareas.stream().filter(t -> t.getEstado() == EstadoTarea.HECHA).count();
		int abiertas = total - hechas;
		int bloqueadas = (int) tareas.stream().filter(t -> t.getEstado() != EstadoTarea.HECHA && t.getBloqueantesPendientes() > 0).count();
		int vencidas = (int) tareas.stream().filter(t -> t.getEstado() != EstadoTarea.HECHA
				&& t.getFechaLimite() != null && t.getFechaLimite().isBefore(hoy)).count();
		int riesgos = (int) notas.stream().filter(ProyectoNota::isAbierta).count();
		int hitosCompletados = (int) hitos.stream().filter(Hito::isCompletado).count();
		int progreso = total > 0 ? Math.round(hechas * 100f / total)
				: hitos.isEmpty() ? 0 : Math.round(hitosCompletados * 100f / hitos.size());

		Set<Long> personas = new HashSet<>();
		personas.add(p.getResponsable().getId());
		miembros.forEach(m -> personas.add(m.getEmpleado().getId()));
		tareas.forEach(t -> personas.add(t.getResponsable().getId()));

		HitoDto proximo = hitos.stream().filter(h -> !h.isCompletado())
				.min(Comparator.comparing(Hito::getFechaObjetivo, Comparator.nullsLast(Comparator.naturalOrder())))
				.map(DtoMapper::hito).orElse(null);
		long hitosVencidos = hitos.stream().filter(h -> !h.isCompletado() && h.getFechaObjetivo() != null
				&& h.getFechaObjetivo().isBefore(hoy)).count();

		return new ProyectoListadoDto(p.getId(), p.getNombre(), p.getObjetivo(), p.getEstado(), p.getPrioridad(),
				DtoMapper.resumen(p.getResponsable()), p.getFechaInicio(), p.getFechaObjetivo(),
				salud(p, abiertas, bloqueadas, vencidas, riesgos, progreso, hitosVencidos, hoy),
				progreso, total, abiertas, bloqueadas, vencidas, riesgos, personas.size(), proximo, participo);
	}

	/**
	 * Rojo: marcado en riesgo, fecha objetivo superada, hitos vencidos, ≥ 3 tareas vencidas o ≥ 50% de lo abierto
	 * esperando a otras tareas. Amarillo: alguna tarea vencida, riesgos o bloqueos abiertos, ≥ 25% esperando,
	 * fecha objetivo en menos de 14 días con menos del 70% hecho, o proyecto pausado.
	 */
	static SaludDto salud(Proyecto p, int abiertas, int bloqueadas, int vencidas, int riesgos, int progreso,
			long hitosVencidos, LocalDate hoy) {
		EstadoProyecto estado = p.getEstado();
		if (estado == EstadoProyecto.COMPLETADO || estado == EstadoProyecto.CANCELADO) {
			return new SaludDto(NivelSalud.VERDE, List.of("Proyecto " + estado.name().toLowerCase(Locale.ROOT)));
		}
		List<String> rojo = new ArrayList<>();
		List<String> amarillo = new ArrayList<>();
		if (estado == EstadoProyecto.EN_RIESGO) rojo.add("Marcado como en riesgo");
		if (estado == EstadoProyecto.PAUSADO) amarillo.add("Proyecto pausado");
		if (p.getFechaObjetivo() != null && estado != EstadoProyecto.PAUSADO) {
			long dias = ChronoUnit.DAYS.between(hoy, p.getFechaObjetivo());
			if (dias < 0) {
				rojo.add("Fecha objetivo superada hace " + (-dias) + " días");
			} else if (dias <= DIAS_AVISO_FECHA_OBJETIVO && progreso < 70) {
				amarillo.add("Quedan " + dias + " días para la fecha objetivo y va al " + progreso + "%");
			}
		}
		if (hitosVencidos > 0) rojo.add(hitosVencidos + " hito(s) vencido(s)");
		if (vencidas >= 3) rojo.add(vencidas + " tareas vencidas");
		else if (vencidas > 0) amarillo.add(vencidas + " tarea(s) vencida(s)");
		int pctBloqueadas = abiertas == 0 ? 0 : Math.round(bloqueadas * 100f / abiertas);
		if (abiertas >= 4 && pctBloqueadas >= 50) rojo.add(pctBloqueadas + "% de las tareas abiertas esperan a otras");
		else if (bloqueadas > 0 && pctBloqueadas >= 25) amarillo.add(pctBloqueadas + "% de las tareas abiertas esperan a otras");
		if (riesgos > 0) amarillo.add(riesgos + " riesgo(s) o bloqueo(s) abierto(s)");

		List<String> motivos = new ArrayList<>(rojo);
		motivos.addAll(amarillo);
		NivelSalud nivel = !rojo.isEmpty() ? NivelSalud.ROJO : !amarillo.isEmpty() ? NivelSalud.AMARILLO : NivelSalud.VERDE;
		return new SaludDto(nivel, motivos.isEmpty() ? List.of("Sin alertas") : motivos);
	}

	private List<MiembroDto> equipo(Proyecto p, List<Tarea> tareas, List<ProyectoMiembro> miembros) {
		Map<Long, Empleado> personas = new LinkedHashMap<>();
		personas.put(p.getResponsable().getId(), p.getResponsable());
		miembros.forEach(m -> personas.putIfAbsent(m.getEmpleado().getId(), m.getEmpleado()));
		tareas.forEach(t -> personas.putIfAbsent(t.getResponsable().getId(), t.getResponsable()));
		Map<Long, ProyectoMiembro> formales = miembros.stream()
				.collect(Collectors.toMap(m -> m.getEmpleado().getId(), m -> m, (a, b) -> a));

		List<MiembroDto> equipo = new ArrayList<>();
		for (Empleado e : personas.values()) {
			List<Tarea> suyas = tareas.stream()
					.filter(t -> t.getResponsable().getId().equals(e.getId()) && t.getEstado() != EstadoTarea.HECHA).toList();
			ProyectoMiembro m = formales.get(e.getId());
			equipo.add(new MiembroDto(DtoMapper.resumen(e), e.getRol(), m != null ? m.getRolProyecto() : null,
					m != null ? m.getDedicacion() : null, e.getId().equals(p.getResponsable().getId()), m != null,
					suyas.size(), (int) suyas.stream().filter(t -> t.getBloqueantesPendientes() > 0).count(),
					suyas.stream().mapToDouble(Tarea::getUrgenciaCalculada).max().orElse(0),
					tareaRepository.countByResponsableIdAndEstadoNot(e.getId(), EstadoTarea.HECHA)));
		}
		equipo.sort(Comparator.comparing((MiembroDto m) -> !m.esResponsable())
				.thenComparing(m -> !m.miembroFormal())
				.thenComparing(Comparator.comparingInt(MiembroDto::tareasAbiertas).reversed()));
		return equipo;
	}

	private Empleado empleado(Long id) {
		return empleadoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empleado", id));
	}

	private static <T> Map<Long, List<T>> agrupar(Collection<T> elementos, Function<T, Long> clave) {
		return elementos.stream().collect(Collectors.groupingBy(clave));
	}
}
