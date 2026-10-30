package com.orgbrain.controller;

import com.orgbrain.dto.TareaDtos.ActualizarTareaRequest;
import com.orgbrain.dto.TareaDtos.CrearTareaRequest;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.service.TareaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tareas")
public class TareaController {

	private final TareaService tareaService;

	public TareaController(TareaService tareaService) {
		this.tareaService = tareaService;
	}

	@GetMapping
	public List<TareaDto> listar() {
		return tareaService.listar();
	}

	@GetMapping("/{id}")
	public TareaDto obtener(@PathVariable Long id) {
		return tareaService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TareaDto crear(@Valid @RequestBody CrearTareaRequest req) {
		return tareaService.crear(req);
	}

	@PatchMapping("/{id}")
	public TareaDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarTareaRequest req) {
		return tareaService.actualizar(id, req);
	}
}
