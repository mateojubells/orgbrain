package com.orgbrain.service.ia;

import com.orgbrain.dto.ChatDtos.ExtraccionDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoDto;
import com.orgbrain.entity.Departamento;
import com.orgbrain.entity.TipoMemoria;
import com.orgbrain.entity.TipoPeticion;
import com.orgbrain.service.ContextoChat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractorHeuristicoTest {

	private final ExtractorHeuristico extractor = new ExtractorHeuristico();
	private final ContextoChat ctx = new ContextoChat(
			new EmpleadoDto(1L, "Ana López García", Departamento.DIRECCION_PROYECTO, "Jefa de proyecto", "ana@x", LocalDate.now(), List.of()),
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of("Orión", "Atlas"), List.of(), LocalDate.now());

	@Test
	void extraePeticionDeLicenciaConProyectoYMemoriaDeProyecto() {
		ExtraccionDto e = extractor.extraer("Estoy desarrollando el proyecto Orion y me falta la licencia de Copilot", ctx);

		assertThat(e.peticiones()).singleElement().satisfies(p -> {
			assertThat(p.tipoPeticion()).isEqualTo(TipoPeticion.LICENCIA);
			assertThat(p.objeto()).isEqualTo("licencia de Copilot");
			assertThat(p.proyectoRelacionado()).isEqualTo("Orión");
			assertThat(p.urgenciaEstimada()).isEqualTo(3);
		});
		assertThat(e.memorias()).singleElement().satisfies(m -> assertThat(m.tipo()).isEqualTo(TipoMemoria.PROYECTO_ACTUAL));
	}

	@Test
	void detectaAccesoUrgente() {
		ExtraccionDto e = extractor.extraer("Necesito acceso a la VPN para poder trabajar hoy mismo", ctx);

		assertThat(e.peticiones()).singleElement().satisfies(p -> {
			assertThat(p.tipoPeticion()).isEqualTo(TipoPeticion.ACCESO);
			assertThat(p.objeto()).isEqualTo("acceso a la VPN");
			assertThat(p.urgenciaEstimada()).isEqualTo(5);
		});
	}

	@Test
	void creaTareaPropiaEnVezDePeticion() {
		ExtraccionDto e = extractor.extraer("Créame una tarea para revisar el presupuesto del proyecto Atlas", ctx);

		assertThat(e.peticiones()).isEmpty();
		assertThat(e.tareasPropias()).singleElement().satisfies(t -> {
			assertThat(t.titulo()).isEqualTo("Revisar el presupuesto");
			assertThat(t.proyectoRelacionado()).isEqualTo("Atlas");
		});
	}

	@Test
	void detectaDependenciasEntreTareasPorNumero() {
		ExtraccionDto e = extractor.extraer("La #301 bloquea a la #302 y la #305 es necesaria para #306", ctx);

		assertThat(e.dependencias()).extracting(d -> d.bloqueante() + ">" + d.bloqueada())
				.containsExactly("#301>#302", "#305>#306");
	}

	@Test
	void detectaConsultasYCambiosDeProyectosYTareas() {
		ExtraccionDto consulta = extractor.extraer("¿Cómo va Orión?", ctx);
		assertThat(consulta.consultasProyecto()).containsExactly("Orión");
		assertThat(consulta.peticiones()).isEmpty();

		ExtraccionDto cambios = extractor.extraer("Pon Atlas en riesgo, añade a Hugo Benítez al proyecto Orión como desarrollador "
				+ "backend. Marca la #303 como hecha", ctx);
		assertThat(cambios.cambiosProyecto()).extracting(c -> c.accion() + ":" + c.proyecto() + ":" + (c.persona() != null ? c.persona() : c.valor()))
				.containsExactly("cambiar_estado:Atlas:en riesgo", "anadir_miembro:Orión:Hugo Benítez");
		assertThat(cambios.cambiosProyecto().get(1).rol()).isEqualTo("desarrollador backend");
		assertThat(cambios.cambiosTarea()).singleElement().satisfies(c -> {
			assertThat(c.tarea()).isEqualTo("#303");
			assertThat(c.estado()).isEqualTo("hecha");
		});

		assertThat(extractor.extraer("¿Cómo van mis proyectos?", ctx).consultasProyecto()).containsExactly("*");
	}

	@Test
	void unSaludoNoGeneraNada() {
		ExtraccionDto e = extractor.extraer("Hola, ¿qué tal?", ctx);

		assertThat(e.peticiones()).isEmpty();
		assertThat(e.tareasPropias()).isEmpty();
		assertThat(e.memorias()).isEmpty();
	}
}
