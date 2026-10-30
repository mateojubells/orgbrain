package com.orgbrain.seed;

import com.orgbrain.entity.*;
import com.orgbrain.repository.*;
import com.orgbrain.service.MotorPriorizacionService;
import com.orgbrain.util.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crea los proyectos de la empresa simulada y los conecta con el trabajo que ya existe.
 * Se ejecuta después de {@link DatosDemoSeeder} y solo si todavía no hay ningún proyecto, así que también sirve de
 * migración para bases de datos creadas antes de la gestión de proyectos:
 * <ul>
 *   <li>Asigna cada tarea a su proyecto según su título (y las subtareas heredan el proyecto del padre).</li>
 *   <li>Convierte las tareas paraguas "Proyecto: …" que creaba el asistente en proyectos reales.</li>
 *   <li>Forma el equipo (responsable + personas con tareas), y añade hitos, riesgos y decisiones de ejemplo.</li>
 * </ul>
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "orgbrain.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DatosProyectosSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DatosProyectosSeeder.class);
	private static final String PREFIJO_PARAGUAS = "Proyecto: ";
	private static final Pattern PROYECTO_EN_DESCRIPCION = Pattern.compile("(?m)^Proyecto: (.+?)\\.?$");

	private final ProyectoRepository proyectoRepository;
	private final ProyectoMiembroRepository miembroRepository;
	private final HitoRepository hitoRepository;
	private final ProyectoNotaRepository notaRepository;
	private final EmpleadoRepository empleadoRepository;
	private final TareaRepository tareaRepository;
	private final DependenciaRepository dependenciaRepository;
	private final MotorPriorizacionService motor;

	public DatosProyectosSeeder(ProyectoRepository proyectoRepository, ProyectoMiembroRepository miembroRepository,
			HitoRepository hitoRepository, ProyectoNotaRepository notaRepository, EmpleadoRepository empleadoRepository,
			TareaRepository tareaRepository, DependenciaRepository dependenciaRepository, MotorPriorizacionService motor) {
		this.proyectoRepository = proyectoRepository;
		this.miembroRepository = miembroRepository;
		this.hitoRepository = hitoRepository;
		this.notaRepository = notaRepository;
		this.empleadoRepository = empleadoRepository;
		this.tareaRepository = tareaRepository;
		this.dependenciaRepository = dependenciaRepository;
		this.motor = motor;
	}

	private record HitoSeed(String nombre, int dias, boolean completado) {
	}

	private record NotaSeed(TipoNotaProyecto tipo, String contenido, int haceDias) {
	}

	private record ProyectoSeed(String nombre, String responsable, int prioridad, EstadoProyecto estado, String objetivo,
			String descripcion, int inicioDias, int objetivoDias, List<String> patrones, List<HitoSeed> hitos,
			List<NotaSeed> notas) {
	}

	private static final List<ProyectoSeed> PROYECTOS = List.of(
			new ProyectoSeed("Orión", "Ana López García", 5, EstadoProyecto.EN_CURSO,
					"Plataforma de recomendaciones personalizadas para clientes",
					"Motor de recomendaciones sobre el histórico de pedidos, con demo al comité y piloto con clientes reales.",
					-90, 25, List.of("orion"),
					List.of(new HitoSeed("Kick-off del proyecto", -90, true), new HitoSeed("Motor de recomendaciones funcional", 4, false),
							new HitoSeed("Demo al comité de dirección", 5, false), new HitoSeed("Piloto con tres clientes", 25, false)),
					List.of(new NotaSeed(TipoNotaProyecto.DECISION, "Usar GitHub Copilot en el equipo de desarrollo para acelerar la entrega.", 20),
							new NotaSeed(TipoNotaProyecto.RIESGO, "Las licencias de Copilot siguen sin asignarse y frenan al equipo de desarrollo.", 3))),
			new ProyectoSeed("Atlas", "Tomás Aguilar Peña", 5, EstadoProyecto.EN_RIESGO,
					"Portal de autoservicio para clientes",
					"Portal web donde los clientes consultan pedidos, facturas e incidencias.",
					-120, 12, List.of("atlas", "portal de clientes"),
					List.of(new HitoSeed("Diseño del portal aprobado", -40, true), new HitoSeed("Certificado y seguridad listos", 2, false),
							new HitoSeed("Go-live del portal", 6, false), new HitoSeed("Formación comercial completada", 12, false)),
					List.of(new NotaSeed(TipoNotaProyecto.RIESGO, "El certificado SSL del portal caduca antes del go-live; sin renovarlo no se puede abrir a clientes.", 5),
							new NotaSeed(TipoNotaProyecto.DECISION, "Abrir primero el portal a las 20 cuentas clave y después al resto de clientes.", 15))),
			new ProyectoSeed("Fénix", "Tomás Aguilar Peña", 4, EstadoProyecto.EN_CURSO,
					"Migración al nuevo ERP",
					"Sustitución del ERP antiguo: maestros, saldos y facturación.",
					-60, 10, List.of("fenix", "erp antiguo", "proveedores duplicados"),
					List.of(new HitoSeed("Análisis de datos maestros", -30, true), new HitoSeed("Migración de proveedores", -3, false),
							new HitoSeed("Arranque en producción", 10, false)),
					List.of(new NotaSeed(TipoNotaProyecto.RIESGO, "Hay proveedores duplicados en el ERP antiguo que pueden romper la migración.", 8))),
			new ProyectoSeed("Nexo", "Diego Santos Iglesias", 4, EstadoProyecto.EN_CURSO,
					"CRM único para todo el equipo comercial",
					"Implantación del CRM Nexo y migración del pipeline desde Excel.",
					-45, 14, List.of("nexo"),
					List.of(new HitoSeed("Modelo de datos acordado", 3, false), new HitoSeed("Migración del pipeline", 9, false),
							new HitoSeed("Primer forecast desde Nexo", 11, false)),
					List.of(new NotaSeed(TipoNotaProyecto.NOTA, "Dirección comercial quiere que el forecast de este trimestre salga ya de Nexo.", 4))),
			new ProyectoSeed("Aurora", "Paula Rey Calvo", 3, EstadoProyecto.EN_CURSO,
					"Campaña de marca internacional",
					"Lanzamiento de la campaña Aurora en Francia y traducción de la web y materiales comerciales.",
					-30, 30, List.of("aurora", "traduccion", "traducir la web", "dossier comercial para distribuidores"),
					List.of(new HitoSeed("Creatividades aprobadas", -10, true), new HitoSeed("Lanzamiento en Francia", 20, false)),
					List.of()),
			new ProyectoSeed("Vega", "Alberto Fuentes León", 3, EstadoProyecto.PLANIFICACION,
					"Automatizar el cierre financiero trimestral",
					"Conciliaciones, tesorería y presentación del cierre con menos trabajo manual.",
					-10, 10, List.of("vega", "nuevo analista financiero", "conciliaciones bancarias del trimestre",
							"informe de tesoreria mensual", "cierre trimestral"),
					List.of(new HitoSeed("Plan de cierre definido", 7, false), new HitoSeed("Cierre trimestral presentado", 9, false)),
					List.of(new NotaSeed(TipoNotaProyecto.RIESGO, "Falta dar de alta en sistemas al nuevo analista y el cierre depende de él.", 2))),
			new ProyectoSeed("Titán", "Irene Prieto Molina", 4, EstadoProyecto.EN_CURSO,
					"Plataforma de datos y cuadros de mando",
					"Procesos ETL desde ERP y CRM y cuadros de mando para dirección.",
					-50, 21, List.of("titan"),
					List.of(new HitoSeed("Entorno de datos listo", 5, false), new HitoSeed("Cuadros de mando publicados", 21, false)),
					List.of(new NotaSeed(TipoNotaProyecto.BLOQUEO, "El entorno de datos se ha quedado sin cuota de Azure.", 1))),
			new ProyectoSeed("Lince", "Ana López García", 3, EstadoProyecto.EN_CURSO,
					"App móvil para clientes",
					"App iOS y Android con login corporativo.",
					-40, 30, List.of("lince"),
					List.of(new HitoSeed("Guía de estilo entregada", 2, false), new HitoSeed("Beta con usuarios piloto", 20, false),
							new HitoSeed("Publicación en tiendas", 30, false)),
					List.of()));

	@Override
	@Transactional
	public void run(String... args) {
		if (proyectoRepository.count() > 0 || empleadoRepository.count() == 0) {
			return;
		}
		List<Tarea> tareas = tareaRepository.findAllByOrderByUrgenciaCalculadaDesc();
		Map<Proyecto, List<String>> patrones = new LinkedHashMap<>();
		LocalDate hoy = LocalDate.now();

		for (ProyectoSeed s : PROYECTOS) {
			Optional<Empleado> responsable = empleadoRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(s.responsable()).stream().findFirst();
			if (responsable.isEmpty()) continue;
			Proyecto p = new Proyecto(s.nombre(), responsable.get());
			p.setPrioridad(s.prioridad());
			p.setEstado(s.estado());
			p.setObjetivo(s.objetivo());
			p.setDescripcion(s.descripcion());
			p.setFechaInicio(hoy.plusDays(s.inicioDias()));
			p.setFechaObjetivo(hoy.plusDays(s.objetivoDias()));
			p.setOrigen(OrigenTarea.SEED);
			proyectoRepository.save(p);
			patrones.put(p, s.patrones());
			for (HitoSeed h : s.hitos()) {
				Hito hito = new Hito(p, h.nombre(), null, hoy.plusDays(h.dias()));
				if (h.completado()) hito.completar(true);
				hitoRepository.save(hito);
			}
			for (NotaSeed n : s.notas()) {
				ProyectoNota nota = new ProyectoNota(p, p.getResponsable(), n.tipo(), n.contenido());
				nota.setFecha(LocalDateTime.now().minusDays(n.haceDias()));
				notaRepository.save(nota);
			}
		}

		int migrados = migrarProyectosDelAsistente(tareas);
		int[] migradosDescripcion = {0};

		// Asignación por título.
		for (Tarea t : tareas) {
			if (t.getProyecto() != null) continue;
			String titulo = " " + Texto.normalizar(t.getTitulo()) + " ";
			patrones.forEach((p, lista) -> {
				if (t.getProyecto() == null && lista.stream().anyMatch(x -> titulo.contains(" " + x + " ") || titulo.contains(" " + x))) {
					t.setProyecto(p);
				}
			});
		}
		// Peticiones del asistente que indicaban su proyecto en la descripción.
		for (Tarea t : tareas) {
			if (t.getProyecto() != null || t.getDescripcion() == null) continue;
			Matcher m = PROYECTO_EN_DESCRIPCION.matcher(t.getDescripcion());
			if (t.getOrigen() == OrigenTarea.CHATBOT && m.find()) {
				String nombre = m.group(1).trim();
				Empleado responsable = t.getSolicitante() != null ? t.getSolicitante() : t.getResponsable();
				t.setProyecto(buscarPorNombre(nombre).orElseGet(() -> {
					migradosDescripcion[0]++;
					return crearDesdeAsistente(nombre, responsable, t.getUrgenciaPropia(), t.getFechaCreacion().toLocalDate());
				}));
			}
		}
		for (int vuelta = 0; vuelta < 3; vuelta++) {
			for (Tarea t : tareas) {
				// Las subtareas heredan el proyecto de su tarea padre...
				if (t.getProyecto() == null && t.getTareaPadre() != null && t.getTareaPadre().getProyecto() != null) {
					t.setProyecto(t.getTareaPadre().getProyecto());
				}
				// ...y una tarea del asistente sin proyecto que hace de padre de otras del asistente, lo hereda de ellas.
				if (t.getProyecto() != null && t.getOrigen() == OrigenTarea.CHATBOT && t.getTareaPadre() != null
						&& t.getTareaPadre().getProyecto() == null && t.getTareaPadre().getOrigen() == OrigenTarea.CHATBOT) {
					t.getTareaPadre().setProyecto(t.getProyecto());
				}
			}
		}

		// Equipo: responsable + quien tiene tareas en el proyecto.
		for (Proyecto p : proyectoRepository.findAll()) {
			Set<Long> anadidos = new HashSet<>();
			miembroRepository.save(new ProyectoMiembro(p, p.getResponsable(), "Responsable del proyecto", 50));
			anadidos.add(p.getResponsable().getId());
			for (Tarea t : tareas) {
				if (p.equals(t.getProyecto()) && anadidos.add(t.getResponsable().getId())) {
					Empleado e = t.getResponsable();
					miembroRepository.save(new ProyectoMiembro(p, e, e.getRol(), 20 + (int) (e.getId() % 3) * 10));
				}
			}
		}

		motor.recalcularTodo();
		long asignadas = tareas.stream().filter(t -> t.getProyecto() != null).count();
		log.info("Seeder de proyectos: {} proyectos ({} migrados desde el asistente), {} de {} tareas asignadas a un proyecto",
				proyectoRepository.count(), migrados + migradosDescripcion[0], asignadas, tareas.size());
	}

	/** Las tareas paraguas "Proyecto: X" pasan a ser proyectos reales; sus subtareas y bloqueantes se asignan a él. */
	private int migrarProyectosDelAsistente(List<Tarea> tareas) {
		int migrados = 0;
		for (Tarea paraguas : tareas) {
			if (paraguas.getOrigen() != OrigenTarea.CHATBOT || !paraguas.getTitulo().startsWith(PREFIJO_PARAGUAS)) continue;
			String nombre = paraguas.getTitulo().substring(PREFIJO_PARAGUAS.length()).trim();
			Proyecto p = buscarPorNombre(nombre).orElseGet(() -> crearDesdeAsistente(nombre, paraguas.getResponsable(),
					paraguas.getUrgenciaPropia(), paraguas.getFechaCreacion().toLocalDate()));
			migrados++;
			for (Tarea t : tareas) {
				if (t.getProyecto() == null && paraguas.equals(t.getTareaPadre())) {
					t.setProyecto(p);
				}
			}
			dependenciaRepository.findByBloqueadaId(paraguas.getId()).forEach(d -> {
				if (d.getBloqueante().getProyecto() == null) d.getBloqueante().setProyecto(p);
			});
			paraguas.setProyecto(p);
			paraguas.setEstado(EstadoTarea.HECHA);
			notaRepository.save(new ProyectoNota(p, paraguas.getResponsable(), TipoNotaProyecto.NOTA,
					"Proyecto creado a partir de la tarea #" + paraguas.getId() + " que había generado el asistente."));
		}
		return migrados;
	}

	private Proyecto crearDesdeAsistente(String nombre, Empleado responsable, int urgencia, LocalDate inicio) {
		Proyecto p = new Proyecto(nombre, responsable);
		p.setPrioridad(Math.max(3, Math.min(5, urgencia)));
		p.setEstado(EstadoProyecto.EN_CURSO);
		p.setOrigen(OrigenTarea.CHATBOT);
		p.setFechaInicio(inicio);
		return proyectoRepository.save(p);
	}

	private Optional<Proyecto> buscarPorNombre(String nombre) {
		String n = Texto.normalizar(nombre);
		return proyectoRepository.findAll().stream().filter(p -> Texto.normalizar(p.getNombre()).equals(n)).findFirst();
	}
}
