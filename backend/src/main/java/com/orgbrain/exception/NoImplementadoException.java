package com.orgbrain.exception;

/** Marca las piezas del esqueleto pendientes de implementar en fases posteriores. Se traduce a HTTP 501. */
public class NoImplementadoException extends RuntimeException {

	public NoImplementadoException(String funcionalidad) {
		super("Pendiente de implementar: " + funcionalidad);
	}
}
