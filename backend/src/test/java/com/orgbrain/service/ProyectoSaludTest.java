package com.orgbrain.service;

import com.orgbrain.dto.ProyectoDtos.NivelSalud;
import com.orgbrain.dto.ProyectoDtos.SaludDto;
import com.orgbrain.entity.Departamento;
import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.EstadoProyecto;
import com.orgbrain.entity.Proyecto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProyectoSaludTest {

	private static final LocalDate HOY = LocalDate.of(2026, 9, 18);

	@Test
	void sinAlertasElProyectoEstaVerde() {
		SaludDto s = salud(EstadoProyecto.EN_CURSO, HOY.plusDays(60), 10, 1, 0, 0, 40, 0);

		assertThat(s.nivel()).isEqualTo(NivelSalud.VERDE);
		assertThat(s.motivos()).containsExactly("Sin alertas");
	}

	@Test
	void unRiesgoAbiertoOUnaTareaVencidaPonenElProyectoEnAmarillo() {
		assertThat(salud(EstadoProyecto.EN_CURSO, null, 10, 0, 0, 1, 50, 0).nivel()).isEqualTo(NivelSalud.AMARILLO);
		assertThat(salud(EstadoProyecto.EN_CURSO, null, 10, 0, 1, 0, 50, 0))
				.satisfies(s -> {
					assertThat(s.nivel()).isEqualTo(NivelSalud.AMARILLO);
					assertThat(s.motivos()).contains("1 tarea(s) vencida(s)");
				});
	}

	@Test
	void fechaSuperadaHitoVencidoOMuchasTareasEsperandoPonenElProyectoEnRojo() {
		assertThat(salud(EstadoProyecto.EN_CURSO, HOY.minusDays(4), 10, 0, 0, 0, 80, 0).motivos())
				.contains("Fecha objetivo superada hace 4 días");
		assertThat(salud(EstadoProyecto.EN_CURSO, null, 10, 0, 0, 0, 80, 1).nivel()).isEqualTo(NivelSalud.ROJO);
		assertThat(salud(EstadoProyecto.EN_CURSO, null, 10, 5, 0, 0, 80, 0))
				.satisfies(s -> {
					assertThat(s.nivel()).isEqualTo(NivelSalud.ROJO);
					assertThat(s.motivos()).contains("50% de las tareas abiertas esperan a otras");
				});
		// Con pocas tareas abiertas no se dispara la alarma aunque el porcentaje sea alto.
		assertThat(salud(EstadoProyecto.EN_CURSO, null, 2, 1, 0, 0, 80, 0).nivel()).isEqualTo(NivelSalud.AMARILLO);
	}

	@Test
	void losProyectosCerradosNoGeneranAlarmas() {
		assertThat(salud(EstadoProyecto.COMPLETADO, HOY.minusDays(30), 5, 5, 3, 2, 100, 2))
				.satisfies(s -> {
					assertThat(s.nivel()).isEqualTo(NivelSalud.VERDE);
					assertThat(s.motivos()).containsExactly("Proyecto completado");
				});
	}

	@Test
	void unaFechaObjetivoCercaConPocoAvanceAvisa() {
		assertThat(salud(EstadoProyecto.EN_CURSO, HOY.plusDays(5), 10, 0, 0, 0, 30, 0).motivos())
				.contains("Quedan 5 días para la fecha objetivo y va al 30%");
	}

	private static SaludDto salud(EstadoProyecto estado, LocalDate fechaObjetivo, int abiertas, int bloqueadas,
			int vencidas, int riesgos, int progreso, long hitosVencidos) {
		Empleado jefe = new Empleado("Ana López", Departamento.DIRECCION_PROYECTO, "Jefa de proyecto", "a@x", HOY);
		Proyecto p = new Proyecto("Orión", jefe);
		p.setEstado(estado);
		p.setFechaObjetivo(fechaObjetivo);
		return ProyectoService.salud(p, abiertas, bloqueadas, vencidas, riesgos, progreso, hitosVencidos, HOY);
	}
}
