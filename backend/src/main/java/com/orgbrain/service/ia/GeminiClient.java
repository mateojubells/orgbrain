package com.orgbrain.service.ia;

import com.orgbrain.config.GeminiProperties;
import com.orgbrain.dto.ChatDtos.*;
import com.orgbrain.dto.EmpleadoDtos.DominioDto;
import com.orgbrain.dto.TareaDtos.TareaDto;
import com.orgbrain.entity.TipoMemoria;
import com.orgbrain.entity.TipoPeticion;
import com.orgbrain.service.ContextoChat;
import com.orgbrain.util.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cliente de la API de Gemini (capa gratuita) vía REST {@code models/{modelo}:generateContent}.
 * <ul>
 *   <li>{@link #extraer}: salida estructurada (responseMimeType=application/json + responseSchema).</li>
 *   <li>{@link #responder}: respuesta conversacional confirmando las acciones realizadas.</li>
 * </ul>
 */
@Component
public class GeminiClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

	private static final String INSTRUCCIONES_EXTRACCION = """
			Eres el módulo de extracción de OrgBrain, el asistente interno de una empresa que organiza el trabajo según \
			las dependencias entre personas. Analiza SOLO el mensaje nuevo del usuario (usa el resto como contexto) y \
			devuelve JSON con el esquema indicado.

			Reglas:
			- "peticiones": cosas que el usuario necesita que haga OTRA persona o departamento (licencias, accesos, \
			información, compras, equipos, gestiones de RRHH...). No incluyas lo que el usuario hará él mismo.
			  Cuenta también lo que quiere conseguir y solo puede tramitar otro departamento aunque no lo pida de forma \
			explícita: "quiero sacarme una certificación" (formación), "habrá que gestionar vuelo y hotel" (viajes), \
			"hay que contratar a dos personas" (altas). Una petición por cada cosa distinta.
			  Si el mensaje vuelve a hablar de una petición que ya está en "Peticiones abiertas que ha hecho el usuario" \
			(o en la conversación reciente) y aporta información nueva (que es más urgente, que le bloquea o para qué \
			proyecto es), inclúyela de nuevo con los datos actualizados: el sistema actualiza la existente, no la duplica.
			  - "tipo_peticion": licencia | acceso | informacion | otro.
			  - "codigo_dominio": el código del directorio de responsabilidades que mejor encaje, o null si ninguno encaja.
			  - "objeto": qué se pide, breve y concreto (p. ej. "licencia de GitHub Copilot").
			  - "proyecto_relacionado": proyecto o iniciativa para la que se necesita, si el usuario lo dice ahora, lo dijo \
			en la conversación reciente o se deduce sin duda de la memoria; si no, null. Si tiene nombre propio, solo el \
			nombre (p. ej. "Orión"); si no, una descripción corta (p. ej. "Automatización del briefing semanal"). Usa \
			exactamente el mismo nombre en todos los elementos del mensaje que pertenezcan a ese proyecto.
			  - "urgencia_estimada": 1-5. 5 = le bloquea, no puede avanzar sin ello o es para hoy; 3 si no hay pistas.
			  - "es_subtarea_de": "#id" o título exacto de una de las tareas abiertas del usuario de la que esto forma \
			parte, o null. No lo uses para expresar orden ("va después de", "no se puede hacer sin"): eso va en \
			"dependencias". Una tarea que va DESPUÉS de otra nunca es subtarea de ella.
			- "tareas_propias": SOLO si el usuario pide explícitamente crear, apuntar o añadir una tarea o subtarea para sí \
			mismo (también "créame la tarea de este proyecto"). Título corto y accionable. Mismo criterio para urgencia, \
			proyecto y "es_subtarea_de". Si la tarea ya existe en sus tareas abiertas, repítela con el mismo título.
			- "dependencias": relaciones de bloqueo que el usuario expresa en este mensaje o que quedan claras con la \
			conversación reciente: "sin X no puedo hacer Y", "X es parte de Y", "Y depende de X", "para Y necesito X".
			  - "bloqueante": lo que tiene que estar hecho antes (X). "bloqueada": lo que espera (Y).
			  - Cada extremo se refiere a una tarea: usa "#id" si ya existe en las listas de tareas o peticiones; si se \
			crea en este mismo mensaje, usa exactamente el "titulo" de la tarea propia o el "objeto" de la petición.
			  - Inclúyela aunque un "es_subtarea_de" o proyecto ya la cubra: el sistema no la duplica.
			  - Revisa el mensaje frase a frase: cada "hasta que no…", "antes de…", "sin… no puedo", "depende de", \
			"va después" es una dependencia. Si un paso intermedio no existe como tarea y el usuario lo describe como \
			trabajo suyo, créalo en tareas_propias para poder encadenarlo.
			  - Nunca crees dependencias en los dos sentidos entre las mismas tareas.
			  - Ejemplo: hay una petición abierta #301 (licencia de Copilot) y el usuario dice "créame la tarea del \
			briefing, la licencia es parte de ello" → tareas_propias [{titulo: "Automatizar el briefing semanal"}] y \
			dependencias [{bloqueante: "#301", bloqueada: "Automatizar el briefing semanal"}].
			- "memorias": hechos útiles para futuras conversaciones, en tercera persona y en una frase:
			  proyecto_actual (en qué proyectos trabaja), necesidad_pendiente (necesidades que NO se han convertido en \
			petición), decision (decisiones tomadas), nota_general (preferencias o datos relevantes).
			  Usa "decision" siempre que diga que ha decidido, acordado o descartado algo ("hemos decidido…", \
			"vamos a hacerlo con…", "he decidido retrasar…").
			  No repitas lo que ya está en la memoria conocida ni guardes peticiones, tareas o dependencias (ya se \
			registran aparte).
			- "consultas_proyecto": nombres de los proyectos por cuyo estado, avance, prioridades, siguientes pasos, \
			equipo, bloqueos o riesgos pregunta ("¿cómo va Orión?", "¿qué falta en Atlas?", "¿quién está en Nexo?"). \
			Usa "*" si pregunta por todos sus proyectos. Lista vacía si no pregunta por proyectos.
			- "cambios_proyecto": cambios que pide hacer sobre proyectos. "accion" es una de:
			  crear_proyecto (valor = objetivo o prioridad; persona = responsable si no es él), cambiar_estado (valor = \
			planificación | en curso | en riesgo | pausado | completado | cancelado), cambiar_prioridad (valor 1-5), \
			cambiar_fecha_objetivo (fecha), cambiar_responsable (persona), anadir_miembro (persona, rol, valor = % de \
			dedicación si lo dice), quitar_miembro (persona), crear_hito (valor = nombre del hito, fecha), completar_hito \
			(valor = nombre del hito), registrar_riesgo / registrar_decision / registrar_bloqueo / registrar_nota (valor = \
			texto en una frase), resolver_riesgo (valor = texto aproximado del riesgo o bloqueo), asignar_tarea (tarea = \
			"#id" o título; mete una tarea existente en el proyecto).
			  - "proyecto": nombre del proyecto tal como aparece en la lista de proyectos (o el nuevo nombre si se crea).
			  - "persona": nombre de la persona tal como lo dice el usuario ("Hugo Benítez"); "yo" si es él mismo.
			  - "fecha": AAAA-MM-DD. Calcula las fechas relativas ("el viernes", "a finales de mes") a partir de la fecha de hoy.
			  - Una decisión o riesgo sobre un proyecto concreto va aquí (registrar_decision / registrar_riesgo) además de \
			en memorias si es útil recordarlo.
			- "cambios_tarea": cambios sobre tareas que ya existen: "tarea" ("#id" o título), "estado" (pendiente | \
			en curso | hecha), "urgencia" (1-5), "fecha_limite" (AAAA-MM-DD), "responsable" (nombre de la persona a la \
			que se reasigna). Deja null lo que no cambie. "Ya he terminado el guion" → estado hecha.
			- Si el mensaje es un saludo o una pregunta sin nada que registrar, devuelve todas las listas vacías.
			""";

	private static final String INSTRUCCIONES_RESPUESTA = """
			Eres OrgBrain, el asistente personal interno de la empresa. Respondes en español, con tono cercano, profesional \
			y breve (máximo 4-5 frases, sin listas largas ni markdown pesado).
			- Si se han realizado acciones, confírmalas con exactitud: a quién se ha asignado cada tarea, su urgencia, \
			con qué tarea o proyecto se ha enlazado y qué bloquea a qué. No inventes acciones que no aparezcan en la \
			lista ni digas que algo queda enlazado si no aparece como acción.
			- Usa la memoria del usuario para mostrar que recuerdas su contexto cuando venga al caso \
			(p. ej. "como estás con el proyecto Orión...").
			- Si hay avisos (sin responsable, duplicados...), explícalos y sugiere qué hacer.
			- Si no hubo acciones, responde con normalidad usando su contexto; si pregunta por sus prioridades, \
			usa la lista de sus tareas ordenadas por urgencia calculada.
			- Si hay "Estado de proyectos", úsalo para contestar preguntas de estado. Puedes extenderte hasta unas 12 líneas \
			con viñetas cortas, en este orden: salud y por qué, avance, lo más prioritario y siguientes pasos (con quién \
			se encarga), cuellos de botella y dependencias de fuera, próximo hito y riesgos abiertos. Da números de tarea \
			(#id) y nombres. Termina con una recomendación concreta de qué mover primero. No inventes datos.
			""";

	private final GeminiProperties props;
	private final JsonMapper json;
	private final RestClient http;

	public GeminiClient(GeminiProperties props, JsonMapper json) {
		this.props = props;
		this.json = json;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.timeoutSeconds())));
		this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
	}

	public boolean disponible() {
		return props.configurada();
	}

	public String modelo() {
		return props.model();
	}

	// ---------------------------------------------------------------- Extracción estructurada

	public ExtraccionDto extraer(String mensaje, ContextoChat ctx) {
		String prompt = contexto(ctx, true) + "\n## Mensaje nuevo del usuario\n" + mensaje;
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("temperature", 0.1);
		config.put("responseMimeType", "application/json");
		config.put("responseSchema", esquemaExtraccion());
		String texto = generar(INSTRUCCIONES_EXTRACCION, prompt, config);
		try {
			return parsearExtraccion(json.readTree(limpiarJson(texto)), fuenteReparacion(mensaje, ctx));
		} catch (JacksonException e) {
			throw new GeminiException("Gemini devolvió un JSON no válido", e);
		}
	}

	/** Textos que el usuario ha escrito o ve, de donde recuperar las letras corrompidas por el modelo. */
	private static String fuenteReparacion(String mensaje, ContextoChat ctx) {
		StringBuilder sb = new StringBuilder(mensaje);
		ctx.historialReciente().forEach(m -> sb.append('\n').append(m.mensaje()));
		ctx.tareasAbiertas().forEach(t -> sb.append('\n').append(t.titulo()));
		ctx.peticionesAbiertas().forEach(t -> sb.append('\n').append(t.titulo()));
		ctx.memoriasRelevantes().forEach(m -> sb.append('\n').append(m.contenido()));
		return sb.toString();
	}

	private Map<String, Object> esquemaExtraccion() {
		Map<String, Object> peticion = objeto(Map.of(
				"tipo_peticion", enumerado("licencia", "acceso", "informacion", "otro"),
				"codigo_dominio", texto(true),
				"objeto", texto(false),
				"proyecto_relacionado", texto(true),
				"urgencia_estimada", Map.of("type", "INTEGER"),
				"es_subtarea_de", texto(true)),
				List.of("tipo_peticion", "objeto", "urgencia_estimada"));
		Map<String, Object> tareaPropia = objeto(Map.of(
				"titulo", texto(false),
				"descripcion", texto(true),
				"urgencia_estimada", Map.of("type", "INTEGER"),
				"proyecto_relacionado", texto(true),
				"es_subtarea_de", texto(true)),
				List.of("titulo", "urgencia_estimada"));
		Map<String, Object> dependencia = objeto(Map.of(
				"bloqueante", texto(false),
				"bloqueada", texto(false)),
				List.of("bloqueante", "bloqueada"));
		Map<String, Object> memoria = objeto(Map.of(
				"tipo", enumerado("proyecto_actual", "necesidad_pendiente", "decision", "nota_general"),
				"contenido", texto(false),
				"proyecto_relacionado", texto(true)),
				List.of("tipo", "contenido"));
		Map<String, Object> cambioProyecto = objeto(Map.of(
				"accion", enumerado("crear_proyecto", "cambiar_estado", "cambiar_prioridad", "cambiar_fecha_objetivo",
						"cambiar_responsable", "anadir_miembro", "quitar_miembro", "crear_hito", "completar_hito",
						"registrar_riesgo", "registrar_decision", "registrar_bloqueo", "registrar_nota", "resolver_riesgo",
						"asignar_tarea"),
				"proyecto", texto(false),
				"valor", texto(true),
				"persona", texto(true),
				"rol", texto(true),
				"fecha", texto(true),
				"tarea", texto(true)),
				List.of("accion", "proyecto"));
		Map<String, Object> cambioTarea = objeto(Map.of(
				"tarea", texto(false),
				"estado", Map.of("type", "STRING", "nullable", true, "enum", List.of("pendiente", "en curso", "hecha")),
				"urgencia", Map.of("type", "INTEGER", "nullable", true),
				"fecha_limite", texto(true),
				"responsable", texto(true)),
				List.of("tarea"));
		Map<String, Object> propiedades = new LinkedHashMap<>();
		propiedades.put("peticiones", Map.of("type", "ARRAY", "items", peticion));
		propiedades.put("tareas_propias", Map.of("type", "ARRAY", "items", tareaPropia));
		propiedades.put("dependencias", Map.of("type", "ARRAY", "items", dependencia));
		propiedades.put("memorias", Map.of("type", "ARRAY", "items", memoria));
		propiedades.put("consultas_proyecto", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
		propiedades.put("cambios_proyecto", Map.of("type", "ARRAY", "items", cambioProyecto));
		propiedades.put("cambios_tarea", Map.of("type", "ARRAY", "items", cambioTarea));
		return objeto(propiedades, List.copyOf(propiedades.keySet()));
	}

	private static Map<String, Object> objeto(Map<String, Object> propiedades, List<String> requeridas) {
		return Map.of("type", "OBJECT", "properties", new LinkedHashMap<>(propiedades), "required", requeridas);
	}

	private static Map<String, Object> texto(boolean nullable) {
		return nullable ? Map.of("type", "STRING", "nullable", true) : Map.of("type", "STRING");
	}

	private static Map<String, Object> enumerado(String... valores) {
		return Map.of("type", "STRING", "enum", List.of(valores));
	}

	ExtraccionDto parsearExtraccion(JsonNode raiz, String fuente) {
		List<PeticionExtraidaDto> peticiones = new ArrayList<>();
		for (JsonNode p : raiz.path("peticiones")) {
			String objeto = leer(fuente, p, "objeto");
			if (objeto == null) continue;
			peticiones.add(new PeticionExtraidaDto(tipoPeticion(leer(fuente, p, "tipo_peticion")), leer(fuente, p, "codigo_dominio"),
					objeto, leer(fuente, p, "proyecto_relacionado"), urgencia(p), leer(fuente, p, "es_subtarea_de")));
		}
		List<TareaPropiaExtraidaDto> tareas = new ArrayList<>();
		for (JsonNode t : raiz.path("tareas_propias")) {
			String titulo = leer(fuente, t, "titulo");
			if (titulo == null) continue;
			tareas.add(new TareaPropiaExtraidaDto(titulo, leer(fuente, t, "descripcion"), urgencia(t),
					leer(fuente, t, "proyecto_relacionado"), leer(fuente, t, "es_subtarea_de")));
		}
		List<MemoriaExtraidaDto> memorias = new ArrayList<>();
		for (JsonNode m : raiz.path("memorias")) {
			String contenido = leer(fuente, m, "contenido");
			TipoMemoria tipo = tipoMemoria(leer(fuente, m, "tipo"));
			if (contenido == null || tipo == null) continue;
			memorias.add(new MemoriaExtraidaDto(tipo, contenido, leer(fuente, m, "proyecto_relacionado")));
		}
		List<DependenciaExtraidaDto> dependencias = new ArrayList<>();
		for (JsonNode d : raiz.path("dependencias")) {
			String bloqueante = leer(fuente, d, "bloqueante");
			String bloqueada = leer(fuente, d, "bloqueada");
			if (bloqueante == null || bloqueada == null) continue;
			dependencias.add(new DependenciaExtraidaDto(bloqueante, bloqueada));
		}
		List<String> consultas = new ArrayList<>();
		for (JsonNode c : raiz.path("consultas_proyecto")) {
			String nombre = c.isNull() ? null : Texto.repararLetras(c.asString().trim(), fuente);
			if (nombre != null && !nombre.isEmpty()) consultas.add(nombre);
		}
		List<CambioProyectoDto> cambiosProyecto = new ArrayList<>();
		for (JsonNode c : raiz.path("cambios_proyecto")) {
			String accion = cadena(c, "accion");
			if (accion == null) continue;
			cambiosProyecto.add(new CambioProyectoDto(accion, leer(fuente, c, "proyecto"), leer(fuente, c, "valor"),
					leer(fuente, c, "persona"), leer(fuente, c, "rol"), cadena(c, "fecha"), leer(fuente, c, "tarea")));
		}
		List<CambioTareaDto> cambiosTarea = new ArrayList<>();
		for (JsonNode c : raiz.path("cambios_tarea")) {
			String tarea = leer(fuente, c, "tarea");
			if (tarea == null) continue;
			JsonNode u = c.get("urgencia");
			cambiosTarea.add(new CambioTareaDto(tarea, cadena(c, "estado"), u == null || u.isNull() ? null : u.asInt(),
					cadena(c, "fecha_limite"), leer(fuente, c, "responsable")));
		}
		return new ExtraccionDto(peticiones, tareas, memorias, dependencias, consultas, cambiosProyecto, cambiosTarea);
	}

	/** Lee un campo de texto reparando las letras que Gemini a veces corrompe (ver {@link Texto#repararLetras}). */
	private static String leer(String fuente, JsonNode nodo, String campo) {
		return Texto.repararLetras(cadena(nodo, campo), fuente);
	}

	private static String cadena(JsonNode nodo, String campo) {
		JsonNode valor = nodo.get(campo);
		if (valor == null || valor.isNull()) return null;
		String s = valor.asString().trim();
		return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
	}

	private static Integer urgencia(JsonNode nodo) {
		JsonNode valor = nodo.get("urgencia_estimada");
		int u = valor == null || valor.isNull() ? 3 : valor.asInt(3);
		return Math.max(1, Math.min(5, u));
	}

	private static TipoPeticion tipoPeticion(String valor) {
		if (valor == null) return TipoPeticion.OTRO;
		try {
			return TipoPeticion.valueOf(valor.toUpperCase(Locale.ROOT).replace("Ó", "O"));
		} catch (IllegalArgumentException e) {
			return TipoPeticion.OTRO;
		}
	}

	private static TipoMemoria tipoMemoria(String valor) {
		if (valor == null) return null;
		try {
			return TipoMemoria.valueOf(valor.toUpperCase(Locale.ROOT).replace("Ó", "O"));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String limpiarJson(String texto) {
		String t = texto.trim();
		if (t.startsWith("```")) {
			t = t.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
		}
		return t;
	}

	// ---------------------------------------------------------------- Respuesta conversacional

	public String responder(String mensaje, ContextoChat ctx, List<String> acciones, List<String> avisos,
			String estadoProyectos) {
		StringBuilder prompt = new StringBuilder(contexto(ctx, false));
		prompt.append("\n## Acciones realizadas por el sistema para este mensaje\n");
		prompt.append(acciones.isEmpty() ? "(ninguna)\n" : acciones.stream().map(a -> "- " + a).collect(Collectors.joining("\n", "", "\n")));
		if (!avisos.isEmpty()) {
			prompt.append("\n## Avisos\n").append(avisos.stream().map(a -> "- " + a).collect(Collectors.joining("\n", "", "\n")));
		}
		if (estadoProyectos != null && !estadoProyectos.isBlank()) {
			prompt.append("\n## Estado de proyectos (datos actuales, tras aplicar las acciones)\n").append(estadoProyectos).append('\n');
		}
		prompt.append("\n## Mensaje nuevo del usuario\n").append(mensaje);
		return generar(INSTRUCCIONES_RESPUESTA, prompt.toString(), Map.of("temperature", 0.6)).trim();
	}

	// ---------------------------------------------------------------- Construcción de contexto

	private String contexto(ContextoChat ctx, boolean incluirDirectorio) {
		StringBuilder sb = new StringBuilder();
		var e = ctx.empleado();
		sb.append("## Usuario\n").append(e.nombre()).append(" — ").append(e.rol()).append(" (").append(e.departamento()).append(")\n");
		sb.append("Fecha de hoy: ").append(ctx.hoy()).append(" (").append(ctx.hoy().getDayOfWeek()
				.getDisplayName(java.time.format.TextStyle.FULL, Locale.of("es", "ES"))).append(")\n");

		sb.append("\n## Memoria conocida del usuario\n");
		if (ctx.memoriasRelevantes().isEmpty()) {
			sb.append("(sin recuerdos relevantes)\n");
		}
		for (MemoriaDto m : ctx.memoriasRelevantes()) {
			sb.append("- [").append(m.tipo().name().toLowerCase(Locale.ROOT)).append("] ").append(m.contenido());
			if (m.proyectoRelacionado() != null) sb.append(" (proyecto: ").append(m.proyectoRelacionado()).append(")");
			sb.append('\n');
		}

		sb.append("\n## Tareas abiertas del usuario (ordenadas por urgencia calculada)\n");
		if (ctx.tareasAbiertas().isEmpty()) {
			sb.append("(ninguna)\n");
		}
		for (TareaDto t : ctx.tareasAbiertas()) {
			sb.append("- #").append(t.id()).append(" «").append(t.titulo()).append("» urgencia propia ")
					.append(t.urgenciaPropia()).append(", calculada ").append(t.urgenciaCalculada());
			if (t.motivoUrgencia() != null) sb.append(" (").append(t.motivoUrgencia()).append(")");
			if (t.bloqueantesPendientes() > 0) sb.append(", esperando a ").append(t.bloqueantesPendientes()).append(" tarea(s)");
			if (t.proyecto() != null) sb.append(", proyecto ").append(t.proyecto().nombre());
			sb.append('\n');
		}

		sb.append("\n## Peticiones abiertas que ha hecho el usuario a otras personas\n");
		if (ctx.peticionesAbiertas().isEmpty()) {
			sb.append("(ninguna)\n");
		}
		for (TareaDto t : ctx.peticionesAbiertas()) {
			sb.append("- #").append(t.id()).append(" «").append(t.titulo()).append("» asignada a ")
					.append(t.responsable().nombre()).append(", estado ").append(t.estado())
					.append(", urgencia propia ").append(t.urgenciaPropia());
			if (t.tareaPadreId() != null) sb.append(", forma parte de la tarea #").append(t.tareaPadreId());
			sb.append('\n');
		}

		if (!ctx.proyectos().isEmpty()) {
			sb.append("\n## Proyectos de la empresa\n");
			ctx.proyectos().forEach(p -> sb.append("- ").append(p).append('\n'));
		}

		if (incluirDirectorio) {
			sb.append("\n## Directorio de responsabilidades (código: nombre [tipo] palabras clave)\n");
			for (DominioDto d : ctx.directorio()) {
				sb.append("- ").append(d.codigo()).append(": ").append(d.nombre()).append(" [")
						.append(d.tipoPeticion().name().toLowerCase(Locale.ROOT)).append("] ").append(d.palabrasClave()).append('\n');
			}
		}

		if (!ctx.historialReciente().isEmpty()) {
			sb.append("\n## Conversación reciente\n");
			for (MensajeChatDto m : ctx.historialReciente()) {
				sb.append(m.rol() == com.orgbrain.entity.RolMensaje.USUARIO ? "Usuario: " : "Asistente: ")
						.append(m.mensaje().replace('\n', ' ')).append('\n');
			}
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- HTTP

	private String generar(String instrucciones, String prompt, Map<String, Object> generationConfig) {
		if (!disponible()) {
			throw new GeminiException("GEMINI_API_KEY no configurada");
		}
		Map<String, Object> cuerpo = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", instrucciones))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
				"generationConfig", generationConfig);
		long inicio = System.currentTimeMillis();
		String respuesta;
		try {
			respuesta = http.post()
					.uri("/models/{modelo}:generateContent", props.model())
					.header("x-goog-api-key", props.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(cuerpo)
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException e) {
			throw new GeminiException("Gemini respondió HTTP " + e.getStatusCode().value() + ": " + resumenError(e.getResponseBodyAsString()), e);
		} catch (RestClientException e) {
			throw new GeminiException("No se pudo contactar con Gemini: " + e.getMessage(), e);
		}
		log.debug("Gemini {} respondió en {} ms", props.model(), System.currentTimeMillis() - inicio);

		try {
			JsonNode raiz = json.readTree(respuesta);
			JsonNode partes = raiz.path("candidates").path(0).path("content").path("parts");
			StringBuilder texto = new StringBuilder();
			for (JsonNode parte : partes) {
				// Los modelos con razonamiento pueden devolver partes "thought"; solo nos interesa la respuesta.
				if (!parte.path("thought").asBoolean(false) && parte.has("text")) {
					texto.append(parte.get("text").asString());
				}
			}
			if (texto.isEmpty()) {
				String motivo = raiz.path("candidates").path(0).path("finishReason").asString("desconocido");
				throw new GeminiException("Gemini no devolvió texto (finishReason: " + motivo + ")");
			}
			return texto.toString();
		} catch (JacksonException e) {
			throw new GeminiException("Respuesta de Gemini ilegible", e);
		}
	}

	private String resumenError(String cuerpo) {
		try {
			String mensaje = json.readTree(cuerpo).path("error").path("message").asString("");
			return mensaje.isBlank() ? cuerpo : mensaje;
		} catch (JacksonException e) {
			return cuerpo;
		}
	}
}
