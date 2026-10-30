package com.orgbrain.repository;

import com.orgbrain.entity.Dependencia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DependenciaRepository extends JpaRepository<Dependencia, Long> {

	boolean existsByBloqueanteIdAndBloqueadaId(Long bloqueanteId, Long bloqueadaId);

	/** Tareas a las que bloquea la tarea dada (aristas salientes). */
	@EntityGraph(attributePaths = {"bloqueada", "bloqueada.responsable"})
	List<Dependencia> findByBloqueanteId(Long bloqueanteId);

	/** Tareas que bloquean a la tarea dada (aristas entrantes). */
	@EntityGraph(attributePaths = {"bloqueante", "bloqueante.responsable"})
	List<Dependencia> findByBloqueadaId(Long bloqueadaId);

	/** Todas las dependencias con sus tareas, responsables y proyectos ya cargados (análisis de proyectos). */
	@EntityGraph(attributePaths = {"bloqueante", "bloqueada", "bloqueante.responsable", "bloqueada.responsable",
			"bloqueante.proyecto", "bloqueada.proyecto"})
	@Query("select d from Dependencia d")
	List<Dependencia> findAllConTareas();
}
