package com.orgbrain.service.ia;

/** Fallo al llamar a Gemini (sin clave, cuota agotada, red, respuesta no válida...). */
public class GeminiException extends RuntimeException {

	public GeminiException(String mensaje) {
		super(mensaje);
	}

	public GeminiException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
