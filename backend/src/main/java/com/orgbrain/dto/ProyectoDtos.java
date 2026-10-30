package com.orgbrain.dto;

import com.orgbrain.dto.EmpleadoDtos.EmpleadoResumenDto;
import com.orgbrain.dto.TareaDtos.AristaGrafoDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.dto.TareaDtos.TareaResumenDto;
import com.orgbrain.entity.EstadoProyecto;
import com.orgbrain.entity.EstadoTarea;
import com.orgbrain.entity.TipoNotaProyecto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProyectoDtos {

	private ProyectoDtos() {
	}

	public enum NivelSalud {
		VERDE, AMARILLO, ROJO
	}

	public record ProyectoResumenDto(Long id, String nombre) {
	}

	public record SaludDto(NivelSalud nivel, List<String> motivos) {
	}

	public record HitoDto(Long id, String nombre, String descripcion, LocalDate fechaObjetivo, boolean completado,
			LocalDate fechaCompletado, boolean vencido) {
	}

	public record NotaDto(Long id, TipoNotaProyecto tipo, String contenido, boolean abierta, EmpleadoResumenDto autor,
			LocalDateTime fecha) {
	}

	/** Fila del listado de proyectos, con los indicadores de un vistazo. */
	public record ProyectoListadoDto(
			Long id, String nombre, String objetivo, EstadoProyecto estado, int prioridad,
			EmpleadoResumenDto responsable, LocalDate fechaInicio, LocalDate fechaObjetivo,
			SaludDto salud, int progreso, int tareasTotales, int tareasAbiertas, int tareasBloqueadas,
			int tareasVencidas, int riesgosAbiertos, int personas, HitoDto proximoHito, boolean participo) {
	}

	/**
	 * Persona que trabaja en el proyecto: miembro formal, responsable o alguien con tareas asignadas.
	 *
	 * @param cargaTotalAbierta tareas abiertas en toda la empresa, para ver si está sobrecargada
	 */
	public record MiembroDto(EmpleadoResumenDto empleado, String rol, String rolProyecto, Integer dedicacion,
			boolean esResponsable, boolean miembroFormal, int tareasAbiertas, int tareasEsperando,
			double urgenciaMaxima, long cargaTotalAbierta) {
	}

	/** Dependencia que cruza la frontera del proyecto (entrante: algo de fuera bloquea al proyecto). */
	public record DependenciaExternaDto(TareaResumenDto bloqueante, ProyectoResumenDto proyectoBloqueante,
			TareaResumenDto bloqueada, ProyectoResumenDto proyectoBloqueada, boolean entrante) {
	}

	public record ProyectoDetalleDto(
			ProyectoListadoDto resumen,
			String descripcion,
			Map<EstadoTarea, Long> tareasPorEstado,
			List<TareaDto> tareas,
			List<TareaDto> prioridades,
			List<TareaDto> siguientesPasos,
			List<TareaDto> cuellosDeBotella,
			List<DependenciaExternaDto> dependenciasExternas,
			List<AristaGrafoDto> dependenciasInternas,
			List<MiembroDto> equipo,
			List<HitoDto> hitos,
			List<NotaDto> notas) {
	}

	public record CrearProyectoRequest(
			@NotBlank @Size(max = 150) String nombre,
			String descripcion,
			@Size(max = 500) String objetivo,
			@NotNull Long responsableId,
			@Min(1) @Max(5) Integer prioridad,
			EstadoProyecto estado,
			LocalDate fechaInicio,
			LocalDate fechaObjetivo) {
	}

	/** Actualización parcial: los campos null no se modifican. */
	public record ActualizarProyectoRequest(
			@Size(max = 150) String nombre,
			String descripcion,
			@Size(max = 500) String objetivo,
			Long responsableId,
			@Min(1) @Max(5) Integer prioridad,
			EstadoProyecto estado,
			LocalDate fechaInicio,
			LocalDate fechaObjetivo) {
	}

	public record MiembroRequest(@NotNull Long empleadoId, @Size(max = 120) String rolProyecto,
			@Min(0) @Max(100) Integer dedicacion) {
	}

	public record HitoRequest(@NotBlank @Size(max = 200) String nombre, @Size(max = 500) String descripcion,
			LocalDate fechaObjetivo) {
	}

	public record ActualizarHitoRequest(@Size(max = 200) String nombre, @Size(max = 500) String descripcion,
			LocalDate fechaObjetivo, Boolean completado) {
	}

	public record NotaRequest(@NotNull TipoNotaProyecto tipo, @NotBlank String contenido) {
	}

	public record ActualizarNotaRequest(@NotNull Boolean abierta) {
	}
}
