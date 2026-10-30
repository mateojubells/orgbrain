package com.orgbrain.repository;

import com.orgbrain.entity.ConversacionChat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversacionChatRepository extends JpaRepository<ConversacionChat, Long> {

	List<ConversacionChat> findByEmpleadoIdOrderByFechaHoraAsc(Long empleadoId);

	/** Últimos N mensajes (orden descendente) para dar contexto corto a Gemini. */
	List<ConversacionChat> findByEmpleadoIdOrderByFechaHoraDesc(Long empleadoId, Pageable pageable);
}
