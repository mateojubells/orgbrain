package com.orgbrain.service;

import com.orgbrain.dto.TareaDtos.RecalculoResultadoDto;
import com.orgbrain.entity.Dependencia;
import com.orgbrain.entity.EstadoProyecto;
import com.orgbrain.entity.EstadoTarea;
import com.orgbrain.entity.Proyecto;
import com.orgbrain.entity.Tarea;
import com.orgbrain.entity.TipoDependencia;
import com.orgbrain.repository.DependenciaRepository;
import com.orgbrain.repository.TareaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Motor de repriorización: calcula la urgencia heredada recorriendo el grafo de dependencias.
 *
 * <p>Para cada tarea abierta (no HECHA):
 * <ol>
 *   <li><b>base</b> = urgencia propia + bonus por fecha límite (vencida +1, ≤3 días +0.5, ≤7 días +0.25)
 *       + bonus del proyecto (prioridad 5 +0.5, prioridad 4 +0.25, en riesgo +0.25), máx. 5.</li>
 *   <li><b>heredada</b> = máximo del <i>nivel</i> de las tareas que bloquea directamente
 *       (×1.0 si la dependencia es DURA, ×0.6 si es BLANDA). Como se calcula de las hojas hacia arriba,
 *       la urgencia se propaga por cadenas largas A→B→C→D.</li>
 *   <li><b>nivel</b> = max(base, heredada). Es lo que se hereda: así el bonus no se acumula eslabón a eslabón.</li>
 *   <li><b>bonus por bloqueo</b> = 0.35 por cada tarea urgente (nivel ≥ 4) bloqueada directa o
 *       transitivamente + 0.1 por cada tarea no urgente, con un máximo de 2.5.</li>
 *   <li><b>calculada</b> = nivel + bonus, entre 1 y 10.</li>
 * </ol>
 * Las dependencias cuyo extremo está HECHA se ignoran: una tarea terminada ya no bloquea.
 * Con 300 tareas el recálculo completo tarda milisegundos, así que se recalcula todo en cada cambio.
 */
@Service
public class MotorPriorizacionService {

	private static final Logger log = LoggerFactory.getLogger(MotorPriorizacionService.class);

	static final double UMBRAL_URGENTE = 4.0;
	static final double FACTOR_BLANDA = 0.6;
	static final double BONUS_POR_URGENTE = 0.35;
	static final double BONUS_POR_NO_URGENTE = 0.1;
	static final double BONUS_MAXIMO = 2.5;
	static final double URGENCIA_MAXIMA = 10.0;

	private final TareaRepository tareaRepository;
	private final DependenciaRepository dependenciaRepository;

	public MotorPriorizacionService(TareaRepository tareaRepository, DependenciaRepository dependenciaRepository) {
		this.tareaRepository = tareaRepository;
		this.dependenciaRepository = dependenciaRepository;
	}

	private record Arista(Tarea destino, TipoDependencia tipo) {
	}

	/** Recalcula urgencia_calculada, motivo y contadores de todas las tareas. */
	@Transactional
	public RecalculoResultadoDto recalcularTodo() {
		long inicio = System.currentTimeMillis();
		List<Tarea> tareas = tareaRepository.findAllByOrderByUrgenciaCalculadaDesc();
		List<Dependencia> dependencias = dependenciaRepository.findAll();
		LocalDate hoy = LocalDate.now();

		// Índice denso para usar BitSet en los conjuntos de descendientes.
		Map<Long, Integer> indice = new HashMap<>();
		for (int i = 0; i < tareas.size(); i++) {
			indice.put(tareas.get(i).getId(), i);
		}
		int n = tareas.size();
		List<List<Arista>> salientes = new ArrayList<>(n);
		List<List<Integer>> entrantes = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			salientes.add(new ArrayList<>());
			entrantes.add(new ArrayList<>());
		}
		for (Dependencia d : dependencias) {
			Integer a = indice.get(d.getBloqueante().getId());
			Integer b = indice.get(d.getBloqueada().getId());
			if (a == null || b == null || !abierta(tareas.get(a)) || !abierta(tareas.get(b))) {
				continue;
			}
			salientes.get(a).add(new Arista(tareas.get(b), d.getTipo()));
			entrantes.get(b).add(a);
		}

		// Kahn inverso: se procesan primero las tareas que no bloquean a nadie.
		int[] pendientesSalida = new int[n];
		Deque<Integer> cola = new ArrayDeque<>();
		for (int i = 0; i < n; i++) {
			pendientesSalida[i] = salientes.get(i).size();
			if (pendientesSalida[i] == 0) {
				cola.add(i);
			}
		}
		double[] nivel = new double[n];
		BitSet[] descendientes = new BitSet[n];
		boolean[] procesada = new boolean[n];
		int subieron = 0;

		while (!cola.isEmpty()) {
			int i = cola.poll();
			procesada[i] = true;
			Tarea t = tareas.get(i);
			if (abierta(t)) {
				double calculada = calcular(t, i, salientes.get(i), indice, nivel, descendientes, tareas, hoy);
				if (calculada > t.getUrgenciaPropia() + 1e-9) {
					subieron++;
				}
			} else {
				descendientes[i] = new BitSet(n);
				aplicar(t, t.getUrgenciaPropia(), null, 0, 0);
			}
			t.setBloqueantesPendientes(entrantes.get(i).size());
			for (int p : entrantes.get(i)) {
				if (--pendientesSalida[p] == 0) {
					cola.add(p);
				}
			}
		}

		// Lo que queda sin procesar está en un ciclo (no debería pasar: se validan al crear dependencias).
		int enCiclo = 0;
		for (int i = 0; i < n; i++) {
			if (!procesada[i]) {
				enCiclo++;
				Tarea t = tareas.get(i);
				aplicar(t, t.getUrgenciaPropia(), "Dependencia circular detectada: revisa sus bloqueos", 0, 0);
				t.setBloqueantesPendientes(entrantes.get(i).size());
			}
		}
		if (enCiclo > 0) {
			log.warn("{} tareas forman parte de ciclos de dependencias", enCiclo);
		}
		long duracion = System.currentTimeMillis() - inicio;
		log.debug("Repriorización: {} tareas, {} subieron de prioridad, {} ms", n, subieron, duracion);
		return new RecalculoResultadoDto(n, subieron, enCiclo, duracion);
	}

	/** Proyectos activos importantes empujan a sus tareas: prioridad 5 +0.5, prioridad 4 +0.25, en riesgo +0.25. */
	static double bonusProyecto(Proyecto p) {
		if (p == null || !p.getEstado().activo()) {
			return 0;
		}
		double bonus = p.getPrioridad() >= 5 ? 0.5 : p.getPrioridad() == 4 ? 0.25 : 0;
		if (p.getEstado() == EstadoProyecto.EN_RIESGO) {
			bonus += 0.25;
		}
		return bonus;
	}

	/** Calcula y aplica la urgencia de {@code t}; deja su nivel en {@code nivel[i]} y devuelve la calculada. */
	private double calcular(Tarea t, int i, List<Arista> salientes, Map<Long, Integer> indice, double[] nivel,
			BitSet[] descendientes, List<Tarea> tareas, LocalDate hoy) {
		List<String> motivos = new ArrayList<>();

		double bonusFecha = 0;
		if (t.getFechaLimite() != null) {
			long dias = ChronoUnit.DAYS.between(hoy, t.getFechaLimite());
			if (dias < 0) {
				bonusFecha = 1.0;
				motivos.add("Fecha límite vencida hace " + (-dias) + " día" + (dias == -1 ? "" : "s"));
			} else if (dias <= 3) {
				bonusFecha = 0.5;
				motivos.add(dias == 0 ? "Vence hoy" : "Vence en " + dias + " día" + (dias == 1 ? "" : "s"));
			} else if (dias <= 7) {
				bonusFecha = 0.25;
				motivos.add("Vence en " + dias + " días");
			}
		}
		double bonusProyecto = bonusProyecto(t.getProyecto());
		if (bonusProyecto > 0) {
			Proyecto p = t.getProyecto();
			motivos.add("Proyecto " + p.getNombre() + " (prioridad " + p.getPrioridad()
					+ (p.getEstado() == EstadoProyecto.EN_RIESGO ? ", en riesgo" : "") + ")");
		}
		double base = Math.min(5.0, t.getUrgenciaPropia() + bonusFecha + bonusProyecto);

		BitSet desc = new BitSet(tareas.size());
		double heredada = 0;
		Tarea origenHerencia = null;
		for (Arista a : salientes) {
			int j = indice.get(a.destino().getId());
			desc.set(j);
			desc.or(descendientes[j]);
			double valor = nivel[j] * (a.tipo() == TipoDependencia.DURA ? 1.0 : FACTOR_BLANDA);
			if (valor > heredada) {
				heredada = valor;
				origenHerencia = a.destino();
			}
		}
		descendientes[i] = desc;

		int urgentes = 0;
		Set<Long> personas = new HashSet<>();
		for (int j = desc.nextSetBit(0); j >= 0; j = desc.nextSetBit(j + 1)) {
			if (nivel[j] >= UMBRAL_URGENTE) {
				urgentes++;
			}
			Long persona = tareas.get(j).getResponsable().getId();
			if (!persona.equals(t.getResponsable().getId())) {
				personas.add(persona);
			}
		}
		int total = desc.cardinality();
		double bonus = Math.min(BONUS_MAXIMO, urgentes * BONUS_POR_URGENTE + (total - urgentes) * BONUS_POR_NO_URGENTE);

		if (heredada > base && origenHerencia != null) {
			motivos.add(0, String.format(Locale.ROOT, "Hereda urgencia %.1f de «%s» (%s)", heredada,
					origenHerencia.getTitulo(), origenHerencia.getResponsable().getNombre()));
		}
		if (total > 0) {
			motivos.add(String.format("Bloquea a %d tarea%s (%d urgente%s) de %d persona%s", total, total == 1 ? "" : "s",
					urgentes, urgentes == 1 ? "" : "s", personas.size(), personas.size() == 1 ? "" : "s"));
		}

		nivel[i] = Math.max(base, heredada);
		double resultado = Math.max(1.0, Math.min(URGENCIA_MAXIMA, nivel[i] + bonus));
		resultado = Math.round(resultado * 100.0) / 100.0;
		String motivo = resultado > t.getUrgenciaPropia() + 1e-9 ? String.join(" · ", motivos) : null;
		aplicar(t, resultado, motivo, total, personas.size());
		return resultado;
	}

	private static void aplicar(Tarea t, double calculada, String motivo, int bloqueadas, int personas) {
		// Solo se tocan los campos si cambian, para que Hibernate no lance UPDATEs innecesarios.
		if (Double.compare(t.getUrgenciaCalculada(), calculada) != 0) t.setUrgenciaCalculada(calculada);
		if (!Objects.equals(t.getMotivoUrgencia(), motivo)) t.setMotivoUrgencia(truncar(motivo));
		if (t.getTareasBloqueadas() != bloqueadas) t.setTareasBloqueadas(bloqueadas);
		if (t.getPersonasBloqueadas() != personas) t.setPersonasBloqueadas(personas);
	}

	private static String truncar(String s) {
		return s == null || s.length() <= 500 ? s : s.substring(0, 497) + "...";
	}

	private static boolean abierta(Tarea t) {
		return t.getEstado() != EstadoTarea.HECHA;
	}

	/** true si añadir la arista bloqueante→bloqueada cerraría un ciclo (la bloqueada ya llega a la bloqueante). */
	@Transactional(readOnly = true)
	public boolean creariaCiclo(Long bloqueanteId, Long bloqueadaId) {
		Map<Long, List<Long>> salientes = new HashMap<>();
		for (Dependencia d : dependenciaRepository.findAll()) {
			salientes.computeIfAbsent(d.getBloqueante().getId(), k -> new ArrayList<>()).add(d.getBloqueada().getId());
		}
		Deque<Long> pila = new ArrayDeque<>(List.of(bloqueadaId));
		Set<Long> vistos = new HashSet<>();
		while (!pila.isEmpty()) {
			Long actual = pila.pop();
			if (actual.equals(bloqueanteId)) {
				return true;
			}
			if (vistos.add(actual)) {
				pila.addAll(salientes.getOrDefault(actual, List.of()));
			}
		}
		return false;
	}
}
