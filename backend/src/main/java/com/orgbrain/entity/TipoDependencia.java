package com.orgbrain.entity;

public enum TipoDependencia {
	/** La tarea bloqueada no puede avanzar hasta que se complete la bloqueante. */
	DURA,
	/** La tarea bloqueada puede avanzar, pero peor o más despacio. */
	BLANDA
}
