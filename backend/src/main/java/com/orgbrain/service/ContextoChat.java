package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.MemoriaDto;
import com.orgbrain.dto.ChatDtos.MensajeChatDto;
import com.orgbrain.dto.EmpleadoDtos.DominioDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoDto;
import com.orgbrain.dto.TareaDtos.TareaDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Instantánea (sin entidades JPA) de todo lo que se le da a Gemini para procesar un mensaje.
 * Permite llamar a la API fuera de la transacción de base de datos.
 *
 * @param proyectosConocidos nombres de todos los proyectos, para reconocerlos en el mensaje
 * @param proyectos          una línea por proyecto con estado, prioridad, responsable y relación con el usuario
 */
public record ContextoChat(
		EmpleadoDto empleado,
		List<MemoriaDto> memoriasRelevantes,
		List<TareaDto> tareasAbiertas,
		List<TareaDto> peticionesAbiertas,
		List<DominioDto> directorio,
		List<MensajeChatDto> historialReciente,
		List<String> proyectosConocidos,
		List<String> proyectos,
		LocalDate hoy) {
}
