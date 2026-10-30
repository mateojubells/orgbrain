package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.*;
import com.orgbrain.entity.*;
import com.orgbrain.repository.*;
import com.orgbrain.service.ia.GeminiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flujo completo del chat con Gemini simulado: reproduce extracciones reales que dieron problemas y las operaciones
 * de gestión de proyectos. Necesita el MySQL/MariaDB de XAMPP; usa una base de datos propia (orgbrain_it) que borra
 * al empezar. Ejecutar con: {@code mvnw test -Dit=true}
 */
@SpringBootTest(properties = {"DB_NAME=orgbrain_it", "GEMINI_API_KEY=simulada"})
@EnabledIfSystemProperty(named = "it", matches = "true")
class ChatFlujoIntegracionTest {

	@MockitoBean
	GeminiClient gemini;

	@Autowired
	ChatService chatService;
	@Autowired
	EmpleadoRepository empleadoRepository;
	@Autowired
	TareaRepository tareaRepository;
	@Autowired
	DependenciaRepository dependenciaRepository;
	@Autowired
	ProyectoRepository proyectoRepository;
	@Autowired
	ProyectoMiembroRepository miembroRepository;
	@Autowired
	HitoRepository hitoRepository;
	@Autowired
	ProyectoNotaRepository notaRepository;

	@BeforeAll
	static void baseDeDatosLimpia() throws Exception {
		try (var c = DriverManager.getConnection("jdbc:mariadb://localhost:3306/", "root", "")) {
			c.createStatement().execute("DROP DATABASE IF EXISTS orgbrain_it");
		}
	}

	@Test
	@Transactional
	void elSeederCreaLosProyectosYLesAsignaLasTareas() {
		assertThat(proyectoRepository.count()).isEqualTo(8);
		assertThat(tareaRepository.findById(1L).orElseThrow().getProyecto().getNombre()).isEqualTo("Orión");
		assertThat(tareaRepository.findById(6L).orElseThrow().getProyecto().getNombre()).isEqualTo("Atlas");
		Proyecto orion = proyectoRepository.findByNombreIgnoreCase("Orión").orElseThrow();
		assertThat(miembroRepository.findByProyectoId(orion.getId())).hasSizeGreaterThan(3);
		assertThat(hitoRepository.findByProyectoIdOrderByFechaObjetivoAscIdAsc(orion.getId())).isNotEmpty();
	}

	@Test
	@Transactional
	void expansionPortugalCreaElProyectoYLasCadenasSinCiclos() {
		Empleado diego = empleado("Diego Santos");
		String proyecto = "Expansión comercial a Portugal";
		ExtraccionDto extraccion = new ExtraccionDto(
				List.of(
						peticion(TipoPeticion.OTRO, "RRHH_ALTAS", "Tramitar contratos e incorporaciones de dos comerciales en Lisboa", proyecto),
						peticion(TipoPeticion.ACCESO, "ACC_APLICACIONES", "Acceso al CRM para dos nuevos comerciales", proyecto),
						peticion(TipoPeticion.OTRO, "HW_EQUIPOS", "Dos portátiles para nuevos comerciales", proyecto),
						peticion(TipoPeticion.OTRO, "RRHH_FORMACION", "Curso intensivo de portugués comercial", proyecto),
						peticion(TipoPeticion.INFORMACION, "FIN_INFORMES", "Informe de rentabilidad y márgenes por región", proyecto),
						peticion(TipoPeticion.ACCESO, "ACC_VPN", "Acceso VPN para trabajar desde Lisboa", proyecto)),
				List.of(
						new TareaPropiaExtraidaDto("Formar a los nuevos comerciales de Lisboa", null, 3, proyecto, null),
						new TareaPropiaExtraidaDto("Elaborar business case para expansión a Portugal", null, 3, proyecto, null),
						// Gemini lo marcó como subtarea del business case: el enlace implícito no debe impedir el explícito.
						new TareaPropiaExtraidaDto("Presentar business case a dirección", null, 3, proyecto,
								"Elaborar business case para expansión a Portugal")),
				List.of(new MemoriaExtraidaDto(TipoMemoria.PROYECTO_ACTUAL, "Lidera la expansión comercial a Portugal.", proyecto)),
				List.of(
						dep("Tramitar contratos e incorporaciones de dos comerciales en Lisboa", "Acceso al CRM para dos nuevos comerciales"),
						dep("Tramitar contratos e incorporaciones de dos comerciales en Lisboa", "Dos portátiles para nuevos comerciales"),
						dep("Acceso al CRM para dos nuevos comerciales", "Formar a los nuevos comerciales de Lisboa"),
						dep("Informe de rentabilidad y márgenes por región", "Elaborar business case para expansión a Portugal"),
						dep("Elaborar business case para expansión a Portugal", "Presentar business case a dirección"),
						dep("#34", "Formar a los nuevos comerciales de Lisboa")));
		when(gemini.disponible()).thenReturn(true);
		when(gemini.extraer(anyString(), any())).thenReturn(extraccion);
		when(gemini.responder(anyString(), any(), anyList(), anyList(), any())).thenReturn("ok");

		ChatResponse r = chatService.procesarMensaje(diego.getId(), new ChatRequest("mensaje de Diego"));

		assertThat(r.avisos()).isEmpty();
		assertThat(bloquea("Tramitar contratos", "Acceso al CRM")).isTrue();
		assertThat(bloquea("Tramitar contratos", "Dos portátiles")).isTrue();
		assertThat(bloquea("Acceso al CRM", "Formar a los nuevos")).isTrue();
		assertThat(bloquea("Informe de rentabilidad", "Elaborar business case")).isTrue();
		assertThat(bloquea("Elaborar business case", "Presentar business case")).isTrue();
		assertThat(bloquea("Presentar business case", "Elaborar business case")).isFalse();
		assertThat(bloquea("Curso intensivo", "Elaborar business case")).isFalse();

		// El proyecto es real (no una tarea paraguas) y contiene las 9 tareas del mensaje.
		Proyecto expansion = proyectoRepository.findByNombreIgnoreCase(proyecto).orElseThrow();
		assertThat(expansion.getResponsable().getId()).isEqualTo(diego.getId());
		assertThat(tareaRepository.findByProyectoIdOrderByUrgenciaCalculadaDesc(expansion.getId())).hasSize(9);
		assertThat(tareaRepository.findAll()).noneMatch(t -> t.getTitulo().startsWith("Proyecto: "));
		assertThat(r.proyectosAfectados()).extracting(p -> p.nombre()).contains(proyecto);

		// #34 ya existía: queda como bloqueante, pero no pasa a ser subtarea de la formación.
		assertThat(tareaRepository.findById(34L).orElseThrow().getTareaPadre()).isNull();
		assertThat(bloquea("Definir los campos obligatorios", "Formar a los nuevos")).isTrue();
		assertThat(r.memoriasGuardadas()).filteredOn(m -> m.tipo() == TipoMemoria.NECESIDAD_PENDIENTE).hasSize(1);
	}

	@Test
	@Transactional
	void repetirUnaPeticionLaActualizaYLaEnlazaConLaTareaQueBloquea() {
		Empleado javier = empleado("Javier Ortega");
		when(gemini.disponible()).thenReturn(true);
		when(gemini.responder(anyString(), any(), anyList(), anyList(), any())).thenReturn("ok");

		when(gemini.extraer(anyString(), any())).thenReturn(new ExtraccionDto(
				List.of(peticion(TipoPeticion.LICENCIA, "LIC_M365", "licencia de Copilot", null)), List.of(), List.of(), List.of()));
		chatService.procesarMensaje(javier.getId(), new ChatRequest("Necesito una licencia de copilot"));

		when(gemini.extraer(anyString(), any())).thenReturn(new ExtraccionDto(
				List.of(new PeticionExtraidaDto(TipoPeticion.LICENCIA, "LIC_M365", "licencia de Copilot",
						"Automatización del briefing semanal", 5, null)),
				List.of(new TareaPropiaExtraidaDto("Automatizar el briefing semanal", null, 4, "Automatización del briefing semanal", null)),
				List.of(),
				List.of(dep("licencia de Copilot", "Automatizar el briefing semanal"))));
		ChatResponse r = chatService.procesarMensaje(javier.getId(), new ChatRequest("Sin la licencia no puedo automatizar el briefing"));

		assertThat(r.tareasCreadas()).extracting(t -> t.titulo()).containsExactly("Automatizar el briefing semanal");
		assertThat(r.tareasActualizadas()).extracting(t -> t.urgenciaPropia()).containsExactly(5);
		assertThat(bloquea("Petición de Javier Ortega: licencia de Copilot", "Automatizar el briefing semanal")).isTrue();
		assertThat(r.tareasCreadas().getFirst().proyecto().nombre()).isEqualTo("Automatización del briefing semanal");
		assertThat(r.avisos()).isEmpty();
	}

	@Test
	@Transactional
	void gestionaProyectosYTareasDesdeElChatYContestaElEstado() {
		Empleado ana = empleado("Ana López");
		when(gemini.disponible()).thenReturn(true);
		when(gemini.responder(anyString(), any(), anyList(), anyList(), any())).thenReturn("ok");
		String fecha = LocalDate.now().plusDays(3).toString();
		when(gemini.extraer(anyString(), any())).thenReturn(new ExtraccionDto(List.of(), List.of(), List.of(), List.of(),
				List.of("Orión"),
				List.of(
						new CambioProyectoDto("cambiar_estado", "Orion", "en riesgo", null, null, null, null),
						new CambioProyectoDto("anadir_miembro", "Orión", null, "Hugo Benítez", "Desarrollo backend", null, null),
						new CambioProyectoDto("crear_hito", "Orión", "Ensayo general", null, null, fecha, null),
						new CambioProyectoDto("registrar_riesgo", "Orión", "El cliente piloto puede retrasar los datos", null, null, null, null),
						new CambioProyectoDto("anadir_miembro", "Orión", null, "Pepito Grillo", null, null, null),
						new CambioProyectoDto("cambiar_estado", "Proyecto Inexistente", "pausado", null, null, null, null)),
				List.of(new CambioTareaDto("#1", "en curso", 5, null, null))));

		ChatResponse r = chatService.procesarMensaje(ana.getId(), new ChatRequest("cambios en Orión"));

		Proyecto orion = proyectoRepository.findByNombreIgnoreCase("Orión").orElseThrow();
		assertThat(orion.getEstado()).isEqualTo(EstadoProyecto.EN_RIESGO);
		assertThat(miembroRepository.findByProyectoId(orion.getId()))
				.anyMatch(m -> m.getEmpleado().getNombre().startsWith("Hugo Benítez") && "Desarrollo backend".equals(m.getRolProyecto()));
		assertThat(hitoRepository.findByProyectoIdOrderByFechaObjetivoAscIdAsc(orion.getId()))
				.anyMatch(h -> h.getNombre().equals("Ensayo general") && h.getFechaObjetivo().toString().equals(fecha));
		assertThat(notaRepository.findByProyectoIdOrderByFechaDesc(orion.getId()))
				.anyMatch(n -> n.getTipo() == TipoNotaProyecto.RIESGO && n.isAbierta() && n.getContenido().contains("cliente piloto"))
				.anyMatch(n -> n.getTipo() == TipoNotaProyecto.CAMBIO && n.getAutor() != null && n.getAutor().getId().equals(ana.getId()));
		Tarea t1 = tareaRepository.findById(1L).orElseThrow();
		assertThat(t1.getEstado()).isEqualTo(EstadoTarea.EN_CURSO);
		assertThat(t1.getUrgenciaPropia()).isEqualTo(5);
		assertThat(r.avisos()).anyMatch(a -> a.contains("Pepito Grillo")).anyMatch(a -> a.contains("Proyecto Inexistente"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<String> estado = ArgumentCaptor.forClass(String.class);
		verify(gemini).responder(anyString(), any(), anyList(), anyList(), estado.capture());
		assertThat(estado.getValue()).contains("### Proyecto Orión").contains("Siguientes pasos").contains("EN_RIESGO");
	}

	private Empleado empleado(String nombre) {
		return empleadoRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre).getFirst();
	}

	private boolean bloquea(String tituloBloqueante, String tituloBloqueada) {
		return dependenciaRepository.findAll().stream().anyMatch(d ->
				d.getBloqueante().getTitulo().contains(tituloBloqueante) && d.getBloqueada().getTitulo().contains(tituloBloqueada));
	}

	private static PeticionExtraidaDto peticion(TipoPeticion tipo, String dominio, String objeto, String proyecto) {
		return new PeticionExtraidaDto(tipo, dominio, objeto, proyecto, 3, null);
	}

	private static DependenciaExtraidaDto dep(String bloqueante, String bloqueada) {
		return new DependenciaExtraidaDto(bloqueante, bloqueada);
	}
}
