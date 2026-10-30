package com.orgbrain.dto;

import com.orgbrain.dto.EmpleadoDtos.EmpleadoResumenDto;
import com.orgbrain.dto.ProyectoDtos.ProyectoResumenDto;
import com.orgbrain.entity.EstadoTarea;
import com.orgbrain.entity.OrigenTarea;
import com.orgbrain.entity.TipoDependencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TareaDtos {

	private TareaDtos() {
	}

	public record TareaDto(Long id, String titulo, String descripcion, EmpleadoResumenDto responsable,
			EmpleadoResumenDto solicitante, ProyectoResumenDto proyecto, Long tareaPadreId, int urgenciaPropia,
			double urgenciaCalculada, String motivoUrgencia, int tareasBloqueadas, int personasBloqueadas,
			int bloqueantesPendientes, EstadoTarea estado, OrigenTarea origen, LocalDateTime fechaCreacion,
			LocalDate fechaLimite) {
	}

	public record TareaResumenDto(Long id, String titulo, EmpleadoResumenDto responsable, EstadoTarea estado,
			int urgenciaPropia, double urgenciaCalculada, TipoDependencia tipoDependencia) {
	}

	/** Vecinos directos de una tarea en el grafo. */
	public record DependenciasDeTareaDto(List<TareaResumenDto> bloquea, List<TareaResumenDto> bloqueadaPor) {
	}

	public record CrearTareaRequest(
			@NotBlank @Size(max = 200) String titulo,
			String descripcion,
			@NotNull Long responsableId,
			Long solicitanteId,
			Long tareaPadreId,
			Long proyectoId,
			@NotNull @Min(1) @Max(5) Integer urgenciaPropia,
			LocalDate fechaLimite) {
	}

	/** Actualización parcial: los campos null no se modifican. {@code proyectoId = 0} quita la tarea de su proyecto. */
	public record ActualizarTareaRequest(
			@Size(max = 200) String titulo,
			String descripcion,
			@Min(1) @Max(5) Integer urgenciaPropia,
			EstadoTarea estado,
			LocalDate fechaLimite,
			Long responsableId,
			Long proyectoId) {
	}

	public record DependenciaDto(Long id, Long bloqueanteId, Long bloqueadaId, TipoDependencia tipo,
			LocalDateTime fechaCreacion) {
	}

	public record CrearDependenciaRequest(@NotNull Long bloqueanteId, @NotNull Long bloqueadaId,
			@NotNull TipoDependencia tipo) {
	}

	public record NodoGrafoDto(Long id, String titulo, EmpleadoResumenDto responsable, ProyectoResumenDto proyecto,
			int urgenciaPropia, double urgenciaCalculada, String motivoUrgencia, int tareasBloqueadas,
			int personasBloqueadas, EstadoTarea estado) {
	}

	public record AristaGrafoDto(Long id, Long origen, Long destino, TipoDependencia tipo) {
	}

	public record GrafoDto(List<NodoGrafoDto> nodos, List<AristaGrafoDto> aristas) {
	}

	public record RecalculoResultadoDto(int tareasRecalculadas, int tareasQueSubieron, int tareasEnCiclo, long duracionMs) {
	}
}
