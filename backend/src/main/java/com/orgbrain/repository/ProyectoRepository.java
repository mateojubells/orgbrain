package com.orgbrain.repository;

import com.orgbrain.entity.Proyecto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

	@EntityGraph(attributePaths = "responsable")
	List<Proyecto> findAllByOrderByPrioridadDescNombreAsc();

	Optional<Proyecto> findByNombreIgnoreCase(String nombre);

	/** Proyectos en los que participa: responsable, miembro o con alguna tarea asignada. */
	@Query("""
			select distinct p from Proyecto p where p.responsable.id = :empleadoId
			or exists (select 1 from ProyectoMiembro m where m.proyecto = p and m.empleado.id = :empleadoId)
			or exists (select 1 from Tarea t where t.proyecto = p and t.responsable.id = :empleadoId)
			""")
	List<Proyecto> findDeEmpleado(Long empleadoId);
}
