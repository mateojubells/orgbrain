package com.orgbrain.controller;

import com.orgbrain.dto.ChatDtos.MemoriaDto;
import com.orgbrain.dto.EmpleadoDtos.EmpleadoDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.service.EmpleadoService;
import com.orgbrain.service.MemoriaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

	private final EmpleadoService empleadoService;
	private final MemoriaService memoriaService;

	public EmpleadoController(EmpleadoService empleadoService, MemoriaService memoriaService) {
		this.empleadoService = empleadoService;
		this.memoriaService = memoriaService;
	}

	/** Lista para el login simulado ("¿quién eres?"). */
	@GetMapping
	public List<EmpleadoDto> listar(@RequestParam(required = false) String q) {
		return empleadoService.listar(q);
	}

	@GetMapping("/{id}")
	public EmpleadoDto obtener(@PathVariable Long id) {
		return empleadoService.obtener(id);
	}

	/** "Mis tareas" ordenadas por urgencia_calculada descendente. */
	@GetMapping("/{id}/tareas")
	public List<TareaDto> tareas(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean incluirHechas) {
		return empleadoService.tareasPriorizadas(id, incluirHechas);
	}

	/** Peticiones que ha hecho a otras personas. */
	@GetMapping("/{id}/peticiones")
	public List<TareaDto> peticiones(@PathVariable Long id) {
		return empleadoService.peticionesRealizadas(id);
	}

	@GetMapping("/{id}/memoria")
	public List<MemoriaDto> memoria(@PathVariable Long id) {
		return memoriaService.listar(id);
	}
}
