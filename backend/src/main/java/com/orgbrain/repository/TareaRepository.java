package com.orgbrain.repository;

import com.orgbrain.entity.EstadoTarea;
import com.orgbrain.entity.Tarea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

	@EntityGraph(attributePaths = {"responsable", "solicitante", "proyecto"})
	List<Tarea> findByResponsableIdOrderByUrgenciaCalculadaDescFechaCreacionAsc(Long responsableId);

	@EntityGraph(attributePaths = {"responsable", "solicitante", "proyecto"})
	List<Tarea> findByResponsableIdAndEstadoNotOrderByUrgenciaCalculadaDescFechaCreacionAsc(Long responsableId, EstadoTarea estado);

	@EntityGraph(attributePaths = {"responsable", "solicitante", "proyecto"})
	List<Tarea> findAllByOrderByUrgenciaCalculadaDesc();

	@EntityGraph(attributePaths = {"responsable", "solicitante", "proyecto"})
	List<Tarea> findByProyectoIdOrderByUrgenciaCalculadaDesc(Long proyectoId);

	/** Carga abierta de una persona: se usa para repartir peticiones entre varios responsables. */
	long countByResponsableIdAndEstadoNot(Long responsableId, EstadoTarea estado);

	@EntityGraph(attributePaths = {"responsable", "solicitante", "proyecto"})
	List<Tarea> findBySolicitanteIdOrderByFechaCreacionDesc(Long solicitanteId);

	/** Peticiones abiertas que ya hizo alguien, para no duplicarlas si repite el mensaje. */
	@EntityGraph(attributePaths = {"responsable", "proyecto"})
	List<Tarea> findBySolicitanteIdAndEstadoNot(Long solicitanteId, EstadoTarea estado);
}
