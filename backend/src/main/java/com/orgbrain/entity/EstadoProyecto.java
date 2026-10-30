package com.orgbrain.entity;

public enum EstadoProyecto {
	PLANIFICACION, EN_CURSO, EN_RIESGO, PAUSADO, COMPLETADO, CANCELADO;

	public boolean activo() {
		return this == PLANIFICACION || this == EN_CURSO || this == EN_RIESGO;
	}
}
