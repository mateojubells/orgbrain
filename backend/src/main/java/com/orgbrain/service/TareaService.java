package com.orgbrain.service;

import com.orgbrain.dto.DtoMapper;
import com.orgbrain.dto.TareaDtos.ActualizarTareaRequest;
import com.orgbrain.dto.TareaDtos.CrearTareaRequest;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.OrigenTarea;
import com.orgbrain.entity.Proyecto;
import com.orgbrain.entity.Tarea;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.EmpleadoRepository;
import com.orgbrain.repository.ProyectoRepository;
import com.orgbrain.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TareaService {

	private final TareaRepository tareaRepository;
	private final EmpleadoRepository empleadoRepository;
	private final ProyectoRepository proyectoRepository;
	private final MotorPriorizacionService motor;

	public TareaService(TareaRepository tareaRepository, EmpleadoRepository empleadoRepository,
			ProyectoRepository proyectoRepository, MotorPriorizacionService motor) {
		this.tareaRepository = tareaRepository;
		this.empleadoRepository = empleadoRepository;
		this.proyectoRepository = proyectoRepository;
		this.motor = motor;
	}

	public List<TareaDto> listar() {
		return tareaRepository.findAllByOrderByUrgenciaCalculadaDesc().stream().map(DtoMapper::tarea).toList();
	}

	public TareaDto obtener(Long id) {
		return DtoMapper.tarea(buscar(id));
	}

	@Transactional
	public TareaDto crear(CrearTareaRequest req) {
		Tarea tarea = new Tarea(req.titulo(), req.descripcion(), empleado(req.responsableId()), req.urgenciaPropia());
		tarea.setOrigen(OrigenTarea.MANUAL);
		if (req.solicitanteId() != null) {
			tarea.setSolicitante(empleado(req.solicitanteId()));
		}
		if (req.tareaPadreId() != null) {
			tarea.setTareaPadre(buscar(req.tareaPadreId()));
			tarea.setProyecto(tarea.getTareaPadre().getProyecto());
		}
		if (req.proyectoId() != null) {
			tarea.setProyecto(proyecto(req.proyectoId()));
		}
		tarea.setFechaLimite(req.fechaLimite());
		tareaRepository.save(tarea);
		motor.recalcularTodo();
		return DtoMapper.tarea(tarea);
	}

	@Transactional
	public TareaDto actualizar(Long id, ActualizarTareaRequest req) {
		Tarea tarea = buscar(id);
		if (req.titulo() != null) tarea.setTitulo(req.titulo());
		if (req.descripcion() != null) tarea.setDescripcion(req.descripcion());
		if (req.urgenciaPropia() != null) tarea.setUrgenciaPropia(req.urgenciaPropia());
		if (req.estado() != null) tarea.setEstado(req.estado());
		if (req.fechaLimite() != null) tarea.setFechaLimite(req.fechaLimite());
		if (req.responsableId() != null) tarea.setResponsable(empleado(req.responsableId()));
		if (req.proyectoId() != null) tarea.setProyecto(req.proyectoId() == 0 ? null : proyecto(req.proyectoId()));
		motor.recalcularTodo();
		return DtoMapper.tarea(tarea);
	}

	public Tarea buscar(Long id) {
		return tareaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Tarea", id));
	}

	private Proyecto proyecto(Long id) {
		return proyectoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proyecto", id));
	}

	private Empleado empleado(Long id) {
		return empleadoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empleado", id));
	}
}
