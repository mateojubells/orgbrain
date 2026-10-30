package com.orgbrain.service;

import com.orgbrain.dto.DtoMapper;
import com.orgbrain.dto.EmpleadoDtos.DominioDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.EstadoTarea;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.DominioResponsabilidadRepository;
import com.orgbrain.repository.EmpleadoRepository;
import com.orgbrain.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmpleadoService {

	private final EmpleadoRepository empleadoRepository;
	private final DominioResponsabilidadRepository dominioRepository;
	private final TareaRepository tareaRepository;

	public EmpleadoService(EmpleadoRepository empleadoRepository, DominioResponsabilidadRepository dominioRepository,
			TareaRepository tareaRepository) {
		this.empleadoRepository = empleadoRepository;
		this.dominioRepository = dominioRepository;
		this.tareaRepository = tareaRepository;
	}

	public List<EmpleadoDto> listar(String texto) {
		List<Empleado> empleados = (texto == null || texto.isBlank())
				? empleadoRepository.findAllByOrderByNombreAsc()
				: empleadoRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(texto.trim());
		return empleados.stream().map(DtoMapper::empleado).toList();
	}

	public EmpleadoDto obtener(Long id) {
		return DtoMapper.empleado(buscar(id));
	}

	/** Tareas del empleado ordenadas por urgencia_calculada descendente. */
	public List<TareaDto> tareasPriorizadas(Long empleadoId, boolean incluirHechas) {
		buscar(empleadoId);
		var tareas = incluirHechas
				? tareaRepository.findByResponsableIdOrderByUrgenciaCalculadaDescFechaCreacionAsc(empleadoId)
				: tareaRepository.findByResponsableIdAndEstadoNotOrderByUrgenciaCalculadaDescFechaCreacionAsc(empleadoId, EstadoTarea.HECHA);
		return tareas.stream().map(DtoMapper::tarea).toList();
	}

	/** Tareas que este empleado ha pedido a otras personas (p. ej. vía asistente). */
	public List<TareaDto> peticionesRealizadas(Long empleadoId) {
		buscar(empleadoId);
		return tareaRepository.findBySolicitanteIdOrderByFechaCreacionDesc(empleadoId).stream().map(DtoMapper::tarea).toList();
	}

	public List<DominioDto> listarDominios() {
		return dominioRepository.findAllByOrderByNombreAsc().stream().map(DtoMapper::dominio).toList();
	}

	public Empleado buscar(Long id) {
		return empleadoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empleado", id));
	}
}
