package com.orgbrain.service;

import com.orgbrain.dto.ChatDtos.MemoriaDto;
import com.orgbrain.dto.DtoMapper;
import com.orgbrain.entity.Empleado;
import com.orgbrain.entity.MemoriaContexto;
import com.orgbrain.entity.TipoMemoria;
import com.orgbrain.exception.RecursoNoEncontradoException;
import com.orgbrain.repository.MemoriaContextoRepository;
import com.orgbrain.util.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Memoria de contexto persistente por empleado.
 * v1: sin embeddings; la relevancia se calcula por coincidencia de palabras clave y proyecto.
 */
@Service
@Transactional(readOnly = true)
public class MemoriaService {

	private static final int PROYECTOS_SIEMPRE_INCLUIDOS = 2;
	private static final double UMBRAL_DUPLICADO = 0.7;

	private final MemoriaContextoRepository memoriaRepository;
	private final EmpleadoService empleadoService;

	public MemoriaService(MemoriaContextoRepository memoriaRepository, EmpleadoService empleadoService) {
		this.memoriaRepository = memoriaRepository;
		this.empleadoService = empleadoService;
	}

	public List<MemoriaDto> listar(Long empleadoId) {
		empleadoService.buscar(empleadoId);
		return memoriaRepository.findByEmpleadoIdOrderByFechaDesc(empleadoId).stream().map(DtoMapper::memoria).toList();
	}

	/**
	 * Recuerdos relevantes para el mensaje entrante:
	 * +2 por cada palabra clave que coincide con el mensaje, +3 si se menciona su proyecto.
	 * Además se incluyen siempre los proyectos actuales más recientes como contexto general.
	 */
	public List<MemoriaDto> buscarRelevantes(Long empleadoId, String mensaje, int limite) {
		List<MemoriaContexto> activas = memoriaRepository.findByEmpleadoIdAndActivaTrueOrderByFechaDesc(empleadoId);
		Set<String> tokensMensaje = Texto.tokens(mensaje);
		String mensajeNormalizado = " " + Texto.normalizar(mensaje) + " ";

		record Puntuada(MemoriaContexto memoria, int puntos) {
		}
		List<Puntuada> puntuadas = new ArrayList<>();
		for (MemoriaContexto m : activas) {
			int puntos = 0;
			for (String clave : palabrasClave(m)) {
				if (tokensMensaje.stream().anyMatch(t -> Texto.coincide(t, clave))) {
					puntos += 2;
				}
			}
			if (m.getProyectoRelacionado() != null
					&& mensajeNormalizado.contains(" " + Texto.normalizar(m.getProyectoRelacionado()) + " ")) {
				puntos += 3;
			}
			puntuadas.add(new Puntuada(m, puntos));
		}

		LinkedHashSet<MemoriaContexto> resultado = new LinkedHashSet<>();
		puntuadas.stream()
				.filter(p -> p.puntos() > 0)
				.sorted(Comparator.comparingInt(Puntuada::puntos).reversed()) // estable: a igualdad, la más reciente
				.limit(limite)
				.forEach(p -> resultado.add(p.memoria()));
		activas.stream()
				.filter(m -> m.getTipo() == TipoMemoria.PROYECTO_ACTUAL)
				.limit(PROYECTOS_SIEMPRE_INCLUIDOS)
				.forEach(resultado::add);
		return resultado.stream().limit(limite + PROYECTOS_SIEMPRE_INCLUIDOS).map(DtoMapper::memoria).toList();
	}

	/**
	 * Guarda un recuerdo salvo que ya exista uno activo equivalente: mismo tipo y ≥70% de palabras en común,
	 * o un proyecto actual del mismo proyecto.
	 */
	@Transactional
	public Optional<MemoriaDto> guardar(Empleado empleado, TipoMemoria tipo, String contenido, String proyecto) {
		Set<String> tokens = Texto.tokens(contenido);
		if (proyecto != null) {
			tokens.addAll(Texto.tokens(proyecto));
		}
		for (MemoriaContexto existente : memoriaRepository.findByEmpleadoIdAndActivaTrueOrderByFechaDesc(empleado.getId())) {
			if (existente.getTipo() != tipo) continue;
			boolean mismoProyecto = tipo == TipoMemoria.PROYECTO_ACTUAL && proyecto != null
					&& existente.getProyectoRelacionado() != null
					&& Texto.normalizar(proyecto).equals(Texto.normalizar(existente.getProyectoRelacionado()));
			if (mismoProyecto || jaccard(tokens, palabrasClave(existente)) >= UMBRAL_DUPLICADO) {
				return Optional.empty();
			}
		}
		MemoriaContexto memoria = new MemoriaContexto(empleado, recortar(contenido), tipo, proyecto, Texto.unirPalabrasClave(tokens));
		return Optional.of(DtoMapper.memoria(memoriaRepository.save(memoria)));
	}

	@Transactional
	public MemoriaDto archivar(Long id) {
		MemoriaContexto m = memoriaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Memoria", id));
		m.setActiva(false);
		return DtoMapper.memoria(m);
	}

	private static Set<String> palabrasClave(MemoriaContexto m) {
		if (m.getPalabrasClave() == null || m.getPalabrasClave().isBlank()) {
			return Texto.tokens(m.getContenido());
		}
		return new LinkedHashSet<>(Arrays.asList(m.getPalabrasClave().split(",")));
	}

	private static double jaccard(Set<String> a, Set<String> b) {
		if (a.isEmpty() && b.isEmpty()) return 1;
		Set<String> interseccion = new HashSet<>(a);
		interseccion.retainAll(b);
		Set<String> union = new HashSet<>(a);
		union.addAll(b);
		return (double) interseccion.size() / union.size();
	}

	private static String recortar(String s) {
		return s.length() <= 2000 ? s : s.substring(0, 2000);
	}
}
