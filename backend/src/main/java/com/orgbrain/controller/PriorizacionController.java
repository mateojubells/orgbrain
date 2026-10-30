package com.orgbrain.controller;

import com.orgbrain.dto.TareaDtos.RecalculoResultadoDto;
import com.orgbrain.service.MotorPriorizacionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/priorizacion")
public class PriorizacionController {

	private final MotorPriorizacionService motor;

	public PriorizacionController(MotorPriorizacionService motor) {
		this.motor = motor;
	}

	/** Recálculo bajo demanda de toda la urgencia heredada. */
	@PostMapping("/recalcular")
	public RecalculoResultadoDto recalcular() {
		return motor.recalcularTodo();
	}
}
