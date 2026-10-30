package com.orgbrain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orgbrain.dto.ProyectoDtos.ProyectoResumenDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.RolMensaje;
import com.orgbrain.entity.TipoMemoria;
import com.orgbrain.entity.TipoPeticion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {

	private ChatDtos() {
	}

	public record ChatRequest(@NotBlank @Size(max = 4000) String mensaje) {
	}

	/** Cómo se ha procesado el mensaje. */
	public enum ModoIa {
		/** Extracción y respuesta generadas por Gemini. */
		GEMINI,
		/** Sin API key configurada: extracción por reglas y respuesta con plantilla. */
		SIN_IA,
		/** Gemini falló (cuota, red...) y se usó el modo sin IA para no perder el mensaje. */
		RESPALDO_SIN_IA
	}

	public record ChatResponse(
			String respuesta,
			ModoIa modoIa,
			ExtraccionDto extraccion,
			List<TareaDto> tareasCreadas,
			List<TareaDto> tareasActualizadas,
			List<ProyectoResumenDto> proyectosAfectados,
			List<MemoriaDto> memoriasGuardadas,
			List<MemoriaDto> memoriasUsadas,
			List<String> acciones,
			List<String> avisos) {
	}

	/** JSON estructurado que se pide a Gemini. Los nombres snake_case coinciden con el esquema del prompt. */
	public record ExtraccionDto(
			@JsonProperty("peticiones") List<PeticionExtraidaDto> peticiones,
			@JsonProperty("tareas_propias") List<TareaPropiaExtraidaDto> tareasPropias,
			@JsonProperty("memorias") List<MemoriaExtraidaDto> memorias,
			@JsonProperty("dependencias") List<DependenciaExtraidaDto> dependencias,
			@JsonProperty("consultas_proyecto") List<String> consultasProyecto,
			@JsonProperty("cambios_proyecto") List<CambioProyectoDto> cambiosProyecto,
			@JsonProperty("cambios_tarea") List<CambioTareaDto> cambiosTarea) {

		public ExtraccionDto(List<PeticionExtraidaDto> peticiones, List<TareaPropiaExtraidaDto> tareasPropias,
				List<MemoriaExtraidaDto> memorias, List<DependenciaExtraidaDto> dependencias) {
			this(peticiones, tareasPropias, memorias, dependencias, List.of(), List.of(), List.of());
		}

		public static ExtraccionDto vacia() {
			return new ExtraccionDto(List.of(), List.of(), List.of(), List.of());
		}
	}

	/**
	 * Cambio sobre un proyecto pedido por lenguaje natural.
	 *
	 * @param accion  crear_proyecto, cambiar_estado, cambiar_prioridad, cambiar_fecha_objetivo, cambiar_responsable,
	 *                anadir_miembro, quitar_miembro, crear_hito, completar_hito, registrar_riesgo, registrar_decision,
	 *                registrar_bloqueo, registrar_nota, resolver_riesgo, asignar_tarea
	 * @param valor   el dato de la acción: estado, prioridad (1-5), nombre del hito, texto del riesgo o decisión, objetivo...
	 * @param persona nombre de la persona afectada (responsable, miembro)
	 * @param fecha   AAAA-MM-DD
	 * @param tarea   "#id" o título, para asignar_tarea
	 */
	public record CambioProyectoDto(
			@JsonProperty("accion") String accion,
			@JsonProperty("proyecto") String proyecto,
			@JsonProperty("valor") String valor,
			@JsonProperty("persona") String persona,
			@JsonProperty("rol") String rol,
			@JsonProperty("fecha") String fecha,
			@JsonProperty("tarea") String tarea) {
	}

	/** Cambio sobre una tarea existente ("marca la #303 como hecha", "pásale el guion a Hugo"). */
	public record CambioTareaDto(
			@JsonProperty("tarea") String tarea,
			@JsonProperty("estado") String estado,
			@JsonProperty("urgencia") Integer urgencia,
			@JsonProperty("fecha_limite") String fechaLimite,
			@JsonProperty("responsable") String responsable) {
	}

	/**
	 * Relación de bloqueo expresada por el usuario ("sin la licencia no puedo hacer el briefing").
	 * Cada extremo es "#id" de una tarea existente, el título de una tarea propia o el objeto de una petición.
	 */
	public record DependenciaExtraidaDto(
			@JsonProperty("bloqueante") String bloqueante,
			@JsonProperty("bloqueada") String bloqueada) {
	}

	/** Algo que el usuario necesita de otra persona o departamento. */
	public record PeticionExtraidaDto(
			@JsonProperty("tipo_peticion") TipoPeticion tipoPeticion,
			@JsonProperty("codigo_dominio") String codigoDominio,
			@JsonProperty("objeto") String objeto,
			@JsonProperty("proyecto_relacionado") String proyectoRelacionado,
			@JsonProperty("urgencia_estimada") Integer urgenciaEstimada,
			@JsonProperty("es_subtarea_de") String esSubtareaDe) {
	}

	/** Tarea o subtarea que el usuario quiere apuntarse a sí mismo. */
	public record TareaPropiaExtraidaDto(
			@JsonProperty("titulo") String titulo,
			@JsonProperty("descripcion") String descripcion,
			@JsonProperty("urgencia_estimada") Integer urgenciaEstimada,
			@JsonProperty("proyecto_relacionado") String proyectoRelacionado,
			@JsonProperty("es_subtarea_de") String esSubtareaDe) {
	}

	public record MemoriaExtraidaDto(
			@JsonProperty("tipo") TipoMemoria tipo,
			@JsonProperty("contenido") String contenido,
			@JsonProperty("proyecto_relacionado") String proyectoRelacionado) {
	}

	public record MensajeChatDto(Long id, RolMensaje rol, String mensaje, LocalDateTime fechaHora) {
	}

	public record MemoriaDto(Long id, TipoMemoria tipo, String contenido, String proyectoRelacionado, boolean activa,
			LocalDateTime fecha) {
	}

	public record EstadoIaDto(boolean geminiConfigurado, String modelo) {
	}
}
