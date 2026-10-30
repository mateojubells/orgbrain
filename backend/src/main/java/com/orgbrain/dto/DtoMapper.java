package com.orgbrain.dto;

import com.orgbrain.dto.ChatDtos.MemoriaDto;
import com.orgbrain.dto.ChatDtos.MensajeChatDto;
import com.orgbrain.dto.EmpleadoDtos.DominioDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoResumenDto;
import com.orgbrain.dto.ProyectoDtos.HitoDto;
import com.orgbrain.dto.ProyectoDtos.NotaDto;
import com.orgbrain.dto.ProyectoDtos.ProyectoResumenDto;
import com.orgbrain.dto.TareaDtos.AristaGrafoDto;
import com.orgbrain.dto.TareaDtos.DependenciaDto;
import com.orgbrain.dto.TareaDtos.NodoGrafoDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.dto.TareaDtos.TareaResumenDto;
import com.orgbrain.entity.*;

import java.time.LocalDate;
import java.util.Comparator;

/** Conversión entidad → DTO. Debe llamarse dentro de una transacción si hay relaciones LAZY. */
public final class DtoMapper {

	private DtoMapper() {
	}

	public static EmpleadoResumenDto resumen(Empleado e) {
		return e == null ? null : new EmpleadoResumenDto(e.getId(), e.getNombre(), e.getDepartamento());
	}

	public static EmpleadoDto empleado(Empleado e) {
		return new EmpleadoDto(e.getId(), e.getNombre(), e.getDepartamento(), e.getRol(), e.getEmail(),
				e.getFechaAlta(),
				e.getDominios().stream().map(DominioResponsabilidad::getNombre).sorted().toList());
	}

	public static DominioDto dominio(DominioResponsabilidad d) {
		return new DominioDto(d.getId(), d.getCodigo(), d.getNombre(), d.getDescripcion(), d.getTipoPeticion(),
				d.getPalabrasClave(),
				d.getResponsables().stream().map(DtoMapper::resumen)
						.sorted(Comparator.comparing(EmpleadoResumenDto::nombre)).toList());
	}

	public static TareaDto tarea(Tarea t) {
		return new TareaDto(t.getId(), t.getTitulo(), t.getDescripcion(), resumen(t.getResponsable()),
				resumen(t.getSolicitante()), proyecto(t.getProyecto()),
				t.getTareaPadre() == null ? null : t.getTareaPadre().getId(),
				t.getUrgenciaPropia(), t.getUrgenciaCalculada(), t.getMotivoUrgencia(), t.getTareasBloqueadas(),
				t.getPersonasBloqueadas(), t.getBloqueantesPendientes(), t.getEstado(), t.getOrigen(),
				t.getFechaCreacion(), t.getFechaLimite());
	}

	public static ProyectoResumenDto proyecto(Proyecto p) {
		return p == null ? null : new ProyectoResumenDto(p.getId(), p.getNombre());
	}

	public static HitoDto hito(Hito h) {
		boolean vencido = !h.isCompletado() && h.getFechaObjetivo() != null && h.getFechaObjetivo().isBefore(LocalDate.now());
		return new HitoDto(h.getId(), h.getNombre(), h.getDescripcion(), h.getFechaObjetivo(), h.isCompletado(),
				h.getFechaCompletado(), vencido);
	}

	public static NotaDto nota(ProyectoNota n) {
		return new NotaDto(n.getId(), n.getTipo(), n.getContenido(), n.isAbierta(), resumen(n.getAutor()), n.getFecha());
	}

	public static TareaResumenDto tareaResumen(Tarea t, TipoDependencia tipo) {
		return new TareaResumenDto(t.getId(), t.getTitulo(), resumen(t.getResponsable()), t.getEstado(),
				t.getUrgenciaPropia(), t.getUrgenciaCalculada(), tipo);
	}

	public static DependenciaDto dependencia(Dependencia d) {
		return new DependenciaDto(d.getId(), d.getBloqueante().getId(), d.getBloqueada().getId(), d.getTipo(),
				d.getFechaCreacion());
	}

	public static NodoGrafoDto nodo(Tarea t) {
		return new NodoGrafoDto(t.getId(), t.getTitulo(), resumen(t.getResponsable()), proyecto(t.getProyecto()),
				t.getUrgenciaPropia(),
				t.getUrgenciaCalculada(), t.getMotivoUrgencia(), t.getTareasBloqueadas(), t.getPersonasBloqueadas(),
				t.getEstado());
	}

	public static AristaGrafoDto arista(Dependencia d) {
		return new AristaGrafoDto(d.getId(), d.getBloqueante().getId(), d.getBloqueada().getId(), d.getTipo());
	}

	public static MensajeChatDto mensaje(ConversacionChat c) {
		return new MensajeChatDto(c.getId(), c.getRol(), c.getMensaje(), c.getFechaHora());
	}

	public static MemoriaDto memoria(MemoriaContexto m) {
		return new MemoriaDto(m.getId(), m.getTipo(), m.getContenido(), m.getProyectoRelacionado(), m.isActiva(),
				m.getFecha());
	}
}
