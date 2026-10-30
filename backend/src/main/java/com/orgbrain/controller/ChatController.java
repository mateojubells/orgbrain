package com.orgbrain.controller;

import com.orgbrain.dto.ChatDtos.ChatRequest;
import com.orgbrain.dto.ChatDtos.ChatResponse;
import com.orgbrain.dto.ChatDtos.EstadoIaDto;
import com.orgbrain.dto.ChatDtos.MemoriaDto;
import com.orgbrain.dto.ChatDtos.MensajeChatDto;
import com.orgbrain.service.ChatService;
import com.orgbrain.service.MemoriaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

	private final ChatService chatService;
	private final MemoriaService memoriaService;

	public ChatController(ChatService chatService, MemoriaService memoriaService) {
		this.chatService = chatService;
		this.memoriaService = memoriaService;
	}

	@PostMapping("/chat/{empleadoId}/mensajes")
	public ChatResponse enviar(@PathVariable Long empleadoId, @Valid @RequestBody ChatRequest req) {
		return chatService.procesarMensaje(empleadoId, req);
	}

	@GetMapping("/chat/{empleadoId}/mensajes")
	public List<MensajeChatDto> historial(@PathVariable Long empleadoId) {
		return chatService.historial(empleadoId);
	}

	/** Si Gemini está configurado (si no, el chat funciona en modo reglas). */
	@GetMapping("/ia/estado")
	public EstadoIaDto estadoIa() {
		return chatService.estadoIa();
	}

	/** Marca un recuerdo como no vigente (necesidad resuelta, decisión obsoleta...). */
	@PatchMapping("/memoria/{id}/archivar")
	public MemoriaDto archivarMemoria(@PathVariable Long id) {
		return memoriaService.archivar(id);
	}
}
