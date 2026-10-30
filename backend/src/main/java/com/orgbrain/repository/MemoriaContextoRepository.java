package com.orgbrain.repository;

import com.orgbrain.entity.MemoriaContexto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoriaContextoRepository extends JpaRepository<MemoriaContexto, Long> {

	List<MemoriaContexto> findByEmpleadoIdOrderByFechaDesc(Long empleadoId);

	List<MemoriaContexto> findByEmpleadoIdAndActivaTrueOrderByFechaDesc(Long empleadoId);
}
