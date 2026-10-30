package com.orgbrain.dto;

import com.orgbrain.entity.Departamento;
import com.orgbrain.entity.TipoPeticion;

import java.time.LocalDate;
import java.util.List;

public final class EmpleadoDtos {

	private EmpleadoDtos() {
	}

	public record EmpleadoResumenDto(Long id, String nombre, Departamento departamento) {
	}

	public record EmpleadoDto(Long id, String nombre, Departamento departamento, String rol, String email,
			LocalDate fechaAlta, List<String> dominios) {
	}

	public record DominioDto(Long id, String codigo, String nombre, String descripcion, TipoPeticion tipoPeticion,
			String palabrasClave, List<EmpleadoResumenDto> responsables) {
	}
}
