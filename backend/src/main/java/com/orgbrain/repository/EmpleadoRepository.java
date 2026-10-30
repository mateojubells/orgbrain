package com.orgbrain.repository;

import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.TipoPeticion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

	@EntityGraph(attributePaths = "dominios")
	List<Empleado> findAllByOrderByNombreAsc();

	@EntityGraph(attributePaths = "dominios")
	List<Empleado> findByNombreContainingIgnoreCaseOrderByNombreAsc(String texto);

	Optional<Empleado> findByEmail(String email);

	@Query("select distinct e from Empleado e join e.dominios d where d.tipoPeticion = :tipo")
	List<Empleado> findResponsablesPorTipoPeticion(TipoPeticion tipo);
}
