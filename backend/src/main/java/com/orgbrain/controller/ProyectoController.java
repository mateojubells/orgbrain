package com.orgbrain.controller;

import com.orgbrain.dto.ProyectoDtos.*;
import com.orgbrain.entity.OrigenTarea;
import com.orgbrain.service.ProyectoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestión de proyectos. El usuario que hace el cambio llega en la cabecera {@code X-Empleado-Id} (login simulado)
 * y queda como autor en el diario del proyecto. Las operaciones de escritura devuelven el detalle actualizado.
 */
@RestController
@RequestMapping("/api")
public class ProyectoController {

	private static final String AUTOR = "X-Empleado-Id";

	private final ProyectoService proyectoService;

	public ProyectoController(ProyectoService proyectoService) {
		this.proyectoService = proyectoService;
	}

	@GetMapping("/proyectos")
	public List<ProyectoListadoDto> listar(@RequestHeader(value = AUTOR, required = false) Long empleadoId) {
		return proyectoService.listar(empleadoId);
	}

	@GetMapping("/proyectos/{id}")
	public ProyectoDetalleDto detalle(@PathVariable Long id, @RequestHeader(value = AUTOR, required = false) Long empleadoId) {
		return proyectoService.detalle(id, empleadoId);
	}

	@PostMapping("/proyectos")
	@ResponseStatus(HttpStatus.CREATED)
	public ProyectoDetalleDto crear(@Valid @RequestBody CrearProyectoRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		return proyectoService.detalle(proyectoService.crear(req, autor, OrigenTarea.MANUAL).getId(), autor);
	}

	@PatchMapping("/proyectos/{id}")
	public ProyectoDetalleDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarProyectoRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		proyectoService.actualizar(id, req, autor);
		return proyectoService.detalle(id, autor);
	}

	@PutMapping("/proyectos/{id}/miembros")
	public ProyectoDetalleDto guardarMiembro(@PathVariable Long id, @Valid @RequestBody MiembroRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		proyectoService.guardarMiembro(id, req, autor);
		return proyectoService.detalle(id, autor);
	}

	@DeleteMapping("/proyectos/{id}/miembros/{empleadoId}")
	public ProyectoDetalleDto quitarMiembro(@PathVariable Long id, @PathVariable Long empleadoId,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		proyectoService.quitarMiembro(id, empleadoId, autor);
		return proyectoService.detalle(id, autor);
	}

	@PostMapping("/proyectos/{id}/hitos")
	public ProyectoDetalleDto crearHito(@PathVariable Long id, @Valid @RequestBody HitoRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		proyectoService.crearHito(id, req, autor);
		return proyectoService.detalle(id, autor);
	}

	@PatchMapping("/hitos/{hitoId}")
	public ProyectoDetalleDto actualizarHito(@PathVariable Long hitoId, @Valid @RequestBody ActualizarHitoRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		Long proyectoId = proyectoService.actualizarHito(hitoId, req, autor).getProyecto().getId();
		return proyectoService.detalle(proyectoId, autor);
	}

	@PostMapping("/proyectos/{id}/notas")
	public ProyectoDetalleDto crearNota(@PathVariable Long id, @Valid @RequestBody NotaRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		proyectoService.crearNota(id, req, autor);
		return proyectoService.detalle(id, autor);
	}

	@PatchMapping("/notas/{notaId}")
	public ProyectoDetalleDto actualizarNota(@PathVariable Long notaId, @Valid @RequestBody ActualizarNotaRequest req,
			@RequestHeader(value = AUTOR, required = false) Long autor) {
		Long proyectoId = proyectoService.actualizarNota(notaId, req, autor).getProyecto().getId();
		return proyectoService.detalle(proyectoId, autor);
	}
}
