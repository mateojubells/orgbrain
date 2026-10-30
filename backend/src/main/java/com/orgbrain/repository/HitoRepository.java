package com.orgbrain.repository;

import com.orgbrain.entity.Hito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HitoRepository extends JpaRepository<Hito, Long> {

	List<Hito> findByProyectoIdOrderByFechaObjetivoAscIdAsc(Long proyectoId);
}
