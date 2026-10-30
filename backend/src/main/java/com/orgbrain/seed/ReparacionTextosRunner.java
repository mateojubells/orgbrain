package com.orgbrain.seed;

import com.orgbrain.entity.ConversacionChat;
import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.MemoriaContexto;
import com.orgbrain.entity.Tarea;
import com.orgbrain.repository.ConversacionChatRepository;
import com.orgbrain.repository.MemoriaContextoRepository;
import com.orgbrain.repository.TareaRepository;
import com.orgbrain.util.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Migración de datos: repara títulos y recuerdos guardados antes de {@link Texto#repararLetras}, cuando Gemini a veces
 * devolvía letras acentuadas convertidas en saltos de línea ("gu\n de uso"). Recupera las letras del historial de chat
 * de las personas implicadas. Idempotente: si no hay textos corruptos no hace nada.
 */
@Component
@Order(3)
public class ReparacionTextosRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(ReparacionTextosRunner.class);

	private final TareaRepository tareaRepository;
	private final MemoriaContextoRepository memoriaRepository;
	private final ConversacionChatRepository chatRepository;

	public ReparacionTextosRunner(TareaRepository tareaRepository, MemoriaContextoRepository memoriaRepository,
			ConversacionChatRepository chatRepository) {
		this.tareaRepository = tareaRepository;
		this.memoriaRepository = memoriaRepository;
		this.chatRepository = chatRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		Map<Long, String> historiales = new HashMap<>();
		int reparados = 0;
		for (Tarea t : tareaRepository.findAll()) {
			if (!corrupto(t.getTitulo())) continue;
			String fuente = historial(t.getResponsable(), historiales) + "\n" + historial(t.getSolicitante(), historiales)
					+ "\n" + (t.getDescripcion() == null ? "" : t.getDescripcion());
			t.setTitulo(Texto.repararLetras(t.getTitulo(), fuente));
			reparados++;
		}
		for (MemoriaContexto m : memoriaRepository.findAll()) {
			if (!corrupto(m.getContenido())) continue;
			m.setContenido(Texto.repararLetras(m.getContenido(), historial(m.getEmpleado(), historiales)));
			reparados++;
		}
		if (reparados > 0) {
			log.info("Reparados {} textos con letras corruptas usando el historial de chat", reparados);
		}
	}

	private static boolean corrupto(String texto) {
		return texto != null && (texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0 || texto.indexOf('�') >= 0);
	}

	private String historial(Empleado e, Map<Long, String> cache) {
		if (e == null) return "";
		return cache.computeIfAbsent(e.getId(), id -> chatRepository.findByEmpleadoIdOrderByFechaHoraAsc(id).stream()
				.map(ConversacionChat::getMensaje).collect(Collectors.joining("\n")));
	}
}
