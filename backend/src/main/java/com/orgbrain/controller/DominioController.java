package com.orgbrain.controller;

import com.orgbrain.dto.EmpleadoDtos.DominioDto;
import com.orgbrain.service.EmpleadoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Directorio "quién hace qué". */
@RestController
@RequestMapping("/api/dominios")
public class DominioController {

	private final EmpleadoService empleadoService;

	public DominioController(EmpleadoService empleadoService) {
		this.empleadoService = empleadoService;
	}

	@GetMapping
	public List<DominioDto> listar() {
		return empleadoService.listarDominios();
	}
}
