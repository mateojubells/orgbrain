package com.orgbrain.repository;

import com.orgbrain.entity.ProyectoNota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoNotaRepository extends JpaRepository<ProyectoNota, Long> {

	@EntityGraph(attributePaths = "autor")
	List<ProyectoNota> findByProyectoIdOrderByFechaDesc(Long proyectoId);
}
