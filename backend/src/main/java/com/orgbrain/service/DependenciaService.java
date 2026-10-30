package com.orgbrain.service;

import com.orgbrain.dto.DtoMapper;
import com.orgbrain.dto.TareaDtos.CrearDependenciaRequest;
import com.orgbrain.dto.TareaDtos.DependenciaDto;
import com.orgbrain.dto.TareaDtos.DependenciasDeTareaDto;
import com.orgbrain.dto.TareaDtos.GrafoDto;
import com.orgbrain.entity.Dependencia;
import com.orgbrain.entity.Tarea;
import com.orgbrain.entity.TipoDependencia;
import com.orgbrain.exception.PeticionInvalidaException;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.DependenciaRepository;
import com.orgbrain.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class DependenciaService {

	private final DependenciaRepository dependenciaRepository;
	private final TareaRepository tareaRepository;
	private final TareaService tareaService;
	private final MotorPriorizacionService motor;

	public DependenciaService(DependenciaRepository dependenciaRepository, TareaRepository tareaRepository,
			TareaService tareaService, MotorPriorizacionService motor) {
		this.dependenciaRepository = dependenciaRepository;
		this.tareaRepository = tareaRepository;
		this.tareaService = tareaService;
		this.motor = motor;
	}

	public List<DependenciaDto> listar() {
		return dependenciaRepository.findAll().stream().map(DtoMapper::dependencia).toList();
	}

	@Transactional
	public DependenciaDto crear(CrearDependenciaRequest req) {
		Dependencia dep = crearSinRecalcular(tareaService.buscar(req.bloqueanteId()),
				tareaService.buscar(req.bloqueadaId()), req.tipo());
		motor.recalcularTodo();
		return DtoMapper.dependencia(dep);
	}

	/** Valida y guarda la arista. Quien llame debe recalcular después (permite agrupar varios cambios). */
	@Transactional(noRollbackFor = PeticionInvalidaException.class)
	public Dependencia crearSinRecalcular(Tarea bloqueante, Tarea bloqueada, TipoDependencia tipo) {
		if (bloqueante.getId().equals(bloqueada.getId())) {
			throw new PeticionInvalidaException("Una tarea no puede bloquearse a sí misma");
		}
		if (dependenciaRepository.existsByBloqueanteIdAndBloqueadaId(bloqueante.getId(), bloqueada.getId())) {
			throw new PeticionInvalidaException("La dependencia ya existe");
		}
		if (motor.creariaCiclo(bloqueante.getId(), bloqueada.getId())) {
			throw new PeticionInvalidaException("La dependencia crearía un ciclo: la tarea " + bloqueada.getId()
					+ " ya bloquea (directa o indirectamente) a la " + bloqueante.getId());
		}
		return dependenciaRepository.save(new Dependencia(bloqueante, bloqueada, tipo));
	}

	@Transactional
	public void eliminar(Long id) {
		Dependencia dep = dependenciaRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Dependencia", id));
		dependenciaRepository.delete(dep);
		motor.recalcularTodo();
	}

	public DependenciasDeTareaDto deTarea(Long tareaId) {
		tareaService.buscar(tareaId);
		return new DependenciasDeTareaDto(
				dependenciaRepository.findByBloqueanteId(tareaId).stream()
						.map(d -> DtoMapper.tareaResumen(d.getBloqueada(), d.getTipo())).toList(),
				dependenciaRepository.findByBloqueadaId(tareaId).stream()
						.map(d -> DtoMapper.tareaResumen(d.getBloqueante(), d.getTipo())).toList());
	}

	/**
	 * Grafo para visualizar.
	 *
	 * @param empleadoId si se indica, solo las componentes conectadas que contienen tareas de ese empleado
	 * @param proyectoId si se indica, las tareas del proyecto y las de fuera conectadas directamente con ellas
	 * @param soloConDependencias si es true, omite las tareas aisladas (sin ninguna dependencia)
	 */
	public GrafoDto grafo(Long empleadoId, Long proyectoId, boolean soloConDependencias) {
		List<Tarea> tareas = tareaRepository.findAllByOrderByUrgenciaCalculadaDesc();
		List<Dependencia> dependencias = dependenciaRepository.findAll();

		Map<Long, List<Long>> vecinos = new HashMap<>();
		for (Dependencia d : dependencias) {
			vecinos.computeIfAbsent(d.getBloqueante().getId(), k -> new ArrayList<>()).add(d.getBloqueada().getId());
			vecinos.computeIfAbsent(d.getBloqueada().getId(), k -> new ArrayList<>()).add(d.getBloqueante().getId());
		}

		Set<Long> incluidas;
		if (proyectoId != null) {
			incluidas = new HashSet<>();
			tareas.stream().filter(t -> t.getProyecto() != null && t.getProyecto().getId().equals(proyectoId))
					.forEach(t -> {
						incluidas.add(t.getId());
						incluidas.addAll(vecinos.getOrDefault(t.getId(), List.of()));
					});
		} else if (empleadoId != null) {
			incluidas = new HashSet<>();
			Deque<Long> pila = new ArrayDeque<>();
			tareas.stream().filter(t -> t.getResponsable().getId().equals(empleadoId))
					.forEach(t -> pila.push(t.getId()));
			while (!pila.isEmpty()) {
				Long id = pila.pop();
				if (incluidas.add(id)) {
					vecinos.getOrDefault(id, List.of()).forEach(pila::push);
				}
			}
		} else {
			incluidas = null;
		}

		List<Tarea> nodos = tareas.stream()
				.filter(t -> incluidas == null || incluidas.contains(t.getId()))
				.filter(t -> !soloConDependencias || vecinos.containsKey(t.getId()))
				.toList();
		Set<Long> ids = new HashSet<>();
		nodos.forEach(t -> ids.add(t.getId()));
		return new GrafoDto(
				nodos.stream().map(DtoMapper::nodo).toList(),
				dependencias.stream()
						.filter(d -> ids.contains(d.getBloqueante().getId()) && ids.contains(d.getBloqueada().getId()))
						.map(DtoMapper::arista).toList());
	}
}
