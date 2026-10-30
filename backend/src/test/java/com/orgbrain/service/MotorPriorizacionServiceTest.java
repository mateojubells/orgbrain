package com.orgbrain.service;

import com.orgbrain.dto.TareaDtos.RecalculoResultadoDto;
import com.orgbrain.entity.*;
import com.orgbrain.repository.DependenciaRepository;
import com.orgbrain.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MotorPriorizacionServiceTest {

	private final TareaRepository tareaRepository = mock(TareaRepository.class);
	private final DependenciaRepository dependenciaRepository = mock(DependenciaRepository.class);
	private final MotorPriorizacionService motor = new MotorPriorizacionService(tareaRepository, dependenciaRepository);

	private final List<Tarea> tareas = new ArrayList<>();
	private final List<Dependencia> dependencias = new ArrayList<>();
	private long siguienteId = 1;

	@BeforeEach
	void setUp() {
		when(tareaRepository.findAllByOrderByUrgenciaCalculadaDesc()).thenReturn(tareas);
		when(dependenciaRepository.findAll()).thenReturn(dependencias);
	}

	@Test
	void tareaPocoUrgenteQueBloqueaATareasUrgentesSubePorEncimaDeEllas() {
		Tarea licencia = tarea("Asignar licencias", 1, 2);
		Tarea a = tarea("Desarrollo A", 2, 5);
		Tarea b = tarea("Desarrollo B", 3, 4);
		Tarea c = tarea("Desarrollo C", 4, 4);
		dep(licencia, a, TipoDependencia.DURA);
		dep(licencia, b, TipoDependencia.DURA);
		dep(licencia, c, TipoDependencia.DURA);

		RecalculoResultadoDto r = motor.recalcularTodo();

		assertThat(licencia.getUrgenciaCalculada()).isGreaterThan(a.getUrgenciaCalculada());
		assertThat(licencia.getUrgenciaCalculada()).isEqualTo(5 + 3 * 0.35, org.assertj.core.data.Offset.offset(0.001));
		assertThat(licencia.getTareasBloqueadas()).isEqualTo(3);
		assertThat(licencia.getPersonasBloqueadas()).isEqualTo(3);
		assertThat(licencia.getMotivoUrgencia()).contains("Bloquea a 3 tareas (3 urgentes) de 3 personas");
		assertThat(a.getBloqueantesPendientes()).isEqualTo(1);
		assertThat(r.tareasQueSubieron()).isEqualTo(1);
	}

	@Test
	void laUrgenciaSePropagaPorCadenasLargasSinAcumularBonusPorEslabon() {
		Tarea t1 = tarea("1", 1, 1);
		Tarea t2 = tarea("2", 2, 1);
		Tarea t3 = tarea("3", 3, 1);
		Tarea t4 = tarea("4", 4, 5);
		dep(t1, t2, TipoDependencia.DURA);
		dep(t2, t3, TipoDependencia.DURA);
		dep(t3, t4, TipoDependencia.DURA);

		motor.recalcularTodo();

		// Todos heredan nivel 5; el bonus depende solo de cuántas urgentes bloquea cada uno.
		assertThat(t3.getUrgenciaCalculada()).isEqualTo(5.35);
		assertThat(t2.getUrgenciaCalculada()).isEqualTo(5.7);
		assertThat(t1.getUrgenciaCalculada()).isEqualTo(6.05);
		assertThat(t1.getTareasBloqueadas()).isEqualTo(3);
	}

	@Test
	void lasDependenciasBlandasTransmitenMenosYLasTareasHechasNoBloquean() {
		Tarea blanda = tarea("blanda", 1, 1);
		Tarea hecha = tarea("hecha", 2, 1);
		Tarea objetivo = tarea("objetivo", 3, 5);
		hecha.setEstado(EstadoTarea.HECHA);
		dep(blanda, objetivo, TipoDependencia.BLANDA);
		dep(hecha, objetivo, TipoDependencia.DURA);

		motor.recalcularTodo();

		assertThat(blanda.getUrgenciaCalculada()).isEqualTo(3.35); // 5 × 0.6 + 0.35
		assertThat(hecha.getUrgenciaCalculada()).isEqualTo(1.0);
		assertThat(hecha.getMotivoUrgencia()).isNull();
		assertThat(objetivo.getBloqueantesPendientes()).isEqualTo(1);
	}

	@Test
	void fechaLimiteVencidaSumaUrgencia() {
		Tarea t = tarea("vencida", 1, 3);
		t.setFechaLimite(LocalDate.now().minusDays(2));

		motor.recalcularTodo();

		assertThat(t.getUrgenciaCalculada()).isEqualTo(4.0);
		assertThat(t.getMotivoUrgencia()).contains("vencida hace 2 días");
	}

	@Test
	void detectaQueUnaAristaCerrariaUnCiclo() {
		Tarea a = tarea("a", 1, 3);
		Tarea b = tarea("b", 2, 3);
		Tarea c = tarea("c", 3, 3);
		dep(a, b, TipoDependencia.DURA);
		dep(b, c, TipoDependencia.DURA);

		assertThat(motor.creariaCiclo(c.getId(), a.getId())).isTrue();
		assertThat(motor.creariaCiclo(a.getId(), c.getId())).isFalse();
	}

	private Tarea tarea(String titulo, long responsableId, int urgencia) {
		Empleado responsable = new Empleado("Persona " + responsableId, Departamento.IT, "Rol", responsableId + "@x", LocalDate.now());
		ReflectionTestUtils.setField(responsable, "id", responsableId);
		Tarea t = new Tarea(titulo, null, responsable, urgencia);
		ReflectionTestUtils.setField(t, "id", siguienteId++);
		tareas.add(t);
		return t;
	}

	private void dep(Tarea bloqueante, Tarea bloqueada, TipoDependencia tipo) {
		dependencias.add(new Dependencia(bloqueante, bloqueada, tipo));
	}
}
