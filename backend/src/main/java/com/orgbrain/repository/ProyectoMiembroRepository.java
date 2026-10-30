package com.orgbrain.repository;

import com.orgbrain.entity.ProyectoMiembro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProyectoMiembroRepository extends JpaRepository<ProyectoMiembro, Long> {

	@EntityGraph(attributePaths = "empleado")
	List<ProyectoMiembro> findByProyectoId(Long proyectoId);

	Optional<ProyectoMiembro> findByProyectoIdAndEmpleadoId(Long proyectoId, Long empleadoId);

	boolean existsByProyectoIdAndEmpleadoId(Long proyectoId, Long empleadoId);
}
