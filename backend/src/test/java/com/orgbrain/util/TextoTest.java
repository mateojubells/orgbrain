package com.orgbrain.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoTest {

	@Test
	void coincidePalabrasConLaMismaRaiz() {
		assertThat(Texto.coincide("licencias", "licencia")).isTrue();
		assertThat(Texto.coincide("automatizar", "automatizacion")).isTrue();
		assertThat(Texto.coincide("configurar", "confirmar")).isFalse();
		assertThat(Texto.coincide("presentacion", "presupuesto")).isFalse();
	}

	@Test
	void tokensIgnoraAcentosYPalabrasVacias() {
		assertThat(Texto.tokens("Automatización del briefing semanal")).containsExactly("automatizacion", "briefing", "semanal");
	}

	@Test
	void reparaLetrasAcentuadasQueGeminiCambiaPorSaltosDeLinea() {
		String mensaje = "Apúntame una tarea para redactar la guía de uso de Copilot. Para configurar las políticas de "
				+ "seguridad necesito permisos de administración en el entorno cloud y dar sesiones de formación.";

		assertThat(Texto.repararLetras("Redactar gu\n de uso de Copilot para empleados", mensaje))
				.isEqualTo("Redactar guía de uso de Copilot para empleados");
		assertThat(Texto.repararLetras("Configurar pol\nticas de seguridad de Copilot", mensaje))
				.isEqualTo("Configurar políticas de seguridad de Copilot");
		assertThat(Texto.repararLetras("permisos de administraci\n en entorno cloud", mensaje))
				.isEqualTo("permisos de administración en entorno cloud");
		assertThat(Texto.repararLetras("Dar sesiones de formaci\n", mensaje))
				.isEqualTo("Dar sesiones de formación");
	}

	@Test
	void siNoPuedeRepararQuitaElCaracterYNoTocaTextoSano() {
		assertThat(Texto.repararLetras("Revisar el caf\n de la sala", "sin pistas")).isEqualTo("Revisar el caf de la sala");
		assertThat(Texto.repararLetras("Texto sin problemas: Orión", "x")).isEqualTo("Texto sin problemas: Orión");
		assertThat(Texto.repararLetras(null, "x")).isNull();
	}
}
