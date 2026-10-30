package com.orgbrain.controller;

import com.orgbrain.dto.TareaDtos.CrearDependenciaRequest;
import com.orgbrain.dto.TareaDtos.DependenciaDto;
import com.orgbrain.dto.TareaDtos.DependenciasDeTareaDto;
import com.orgbrain.dto.TareaDtos.GrafoDto;
import com.orgbrain.service.DependenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DependenciaController {

	private final DependenciaService dependenciaService;

	public DependenciaController(DependenciaService dependenciaService) {
		this.dependenciaService = dependenciaService;
	}

	@GetMapping("/dependencias")
	public List<DependenciaDto> listar() {
		return dependenciaService.listar();
	}

	@PostMapping("/dependencias")
	@ResponseStatus(HttpStatus.CREATED)
	public DependenciaDto crear(@Valid @RequestBody CrearDependenciaRequest req) {
		return dependenciaService.crear(req);
	}

	@DeleteMapping("/dependencias/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		dependenciaService.eliminar(id);
	}

	/** Qué bloquea una tarea y quién la bloquea a ella. */
	@GetMapping("/tareas/{id}/dependencias")
	public DependenciasDeTareaDto deTarea(@PathVariable Long id) {
		return dependenciaService.deTarea(id);
	}

	/** Nodos (tareas) y aristas (dependencias) para la vista de grafo. */
	@GetMapping("/grafo")
	public GrafoDto grafo(@RequestParam(required = false) Long empleadoId,
			@RequestParam(required = false) Long proyectoId,
			@RequestParam(defaultValue = "true") boolean soloConDependencias) {
		return dependenciaService.grafo(empleadoId, proyectoId, soloConDependencias);
	}
}
