package com.orgbrain.repository;

import com.orgbrain.entity.DominioResponsabilidad;
import com.orgbrain.entity.TipoPeticion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DominioResponsabilidadRepository extends JpaRepository<DominioResponsabilidad, Long> {

	Optional<DominioResponsabilidad> findByCodigo(String codigo);

	@EntityGraph(attributePaths = "responsables")
	List<DominioResponsabilidad> findAllByOrderByNombreAsc();

	@EntityGraph(attributePaths = "responsables")
	List<DominioResponsabilidad> findByTipoPeticion(TipoPeticion tipoPeticion);
}
