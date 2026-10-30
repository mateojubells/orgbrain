package com.orgbrain.service.ia;

import com.orgbrain.dto.ChatDtos.CambioProyectoDto;
import com.orgbrain.dto.ChatDtos.CambioTareaDto;
import com.orgbrain.dto.ChatDtos.DependenciaExtraidaDto;
import com.orgbrain.dto.ChatDtos.ExtraccionDto;
import com.orgbrain.dto.ChatDtos.MemoriaExtraidaDto;
import com.orgbrain.dto.ChatDtos.PeticionExtraidaDto;
import com.orgbrain.dto.ChatDtos.TareaPropiaExtraidaDto;
import com.orgbrain.entity.TipoMemoria;
import com.orgbrain.entity.TipoPeticion;
import com.orgbrain.service.ContextoChat;
import com.orgbrain.util.Texto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción por reglas para cuando no hay API key de Gemini o la llamada falla.
 * Cubre las frases típicas de la demo ("necesito una licencia de Copilot para el proyecto Orión",
 * "créame una tarea para revisar el presupuesto", "estoy liderando el proyecto X", "hemos decidido...").
 */
@Component
public class ExtractorHeuristico {

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final Pattern PETICION = Pattern.compile(
			"(?:necesito|necesitamos|me falta|nos falta|me hace falta|nos hace falta|me faltan|nos faltan|no tengo|no tenemos"
					+ "|solicito|quiero pedir|quisiera pedir|tengo que pedir|pedir|me pueden dar|me podéis dar|me podeis dar|requiero)"
					+ "\\s+(.+?)(?=\\s+(?:para (?:el|la|mi|nuestro) proyecto|para (?:poder|trabajar|que|hacer|empezar|terminar|acabar|seguir)|en el proyecto|del proyecto|porque|ya que|y (?:estoy|necesito|me)|antes de)|[.;!?\\n]|,\\s|$)",
			FLAGS);
	private static final Pattern TAREA_PROPIA = Pattern.compile(
			"(?:cr[eé]a(?:me)?|ap[uú]nta(?:me)?|a[nñ]ade(?:me)?|an[oó]ta(?:me)?|pon(?:me)?|agrega(?:me)?)\\s+(?:una\\s+)?(sub)?tarea\\s+(?:para\\s+|de\\s+|:\\s*)?(.+?)(?=\\s+(?:dentro del|como subtarea de|en el proyecto|del proyecto|para el proyecto)|[.;!?\\n]|$)",
			FLAGS);
	private static final Pattern SUBTAREA_DE = Pattern.compile(
			"(?:subtarea de|dentro de(?:l| la)?|parte de(?:l| la)?)\\s+(?:tarea\\s+)?[«\"']?(.+?)[»\"']?(?=[.;,!?\\n]|$)", FLAGS);
	private static final Pattern PROYECTO = Pattern.compile("proyecto\\s+[«\"']?([\\p{L}0-9][\\p{L}0-9-]*)", FLAGS);
	private static final Pattern TRABAJANDO_EN = Pattern.compile(
			"(?:estoy|estamos)\\s+(?:desarrollando|trabajando en|liderando|llevando|montando|preparando|con)\\s+(.+?)(?=\\s+y\\s+(?:me|nos|necesito|necesitamos)|[.;!?\\n]|,\\s|$)",
			FLAGS);
	private static final Pattern DECISION = Pattern.compile(
			"(?:hemos|he)\\s+decidido\\s+(.+?)(?=[.;!?\\n]|$)", FLAGS);
	private static final Pattern URGENTE = Pattern.compile(
			"urgente|urgencia|cuanto antes|lo antes posible|asap|hoy mismo|para hoy|ya mismo|bloquead|me bloquea|nos bloquea|cr[ií]tico", FLAGS);
	private static final Pattern PRONTO = Pattern.compile("pronto|esta semana|ma[nñ]ana|en breve", FLAGS);
	private static final Pattern SIN_PRISA = Pattern.compile("sin prisa|cuando pueda|no corre prisa|cuando puedas", FLAGS);
	private static final Pattern CONSULTA = Pattern.compile(
			"c[oó]mo (?:va|van|vamos|est[aá])|estado|status|situaci[oó]n|resumen|qu[eé] (?:falta|queda)|siguientes pasos|"
					+ "prioridades|qui[eé]n (?:est[aá]|trabaja)|riesgos|bloqueos", FLAGS);
	private static final Pattern MIS_PROYECTOS = Pattern.compile("mis proyectos|todos (?:los|mis) proyectos", FLAGS);
	/** "pon Atlas en riesgo", "marca el proyecto Vega como pausado" */
	private static final Pattern ESTADO_PROYECTO = Pattern.compile(
			"(?:pon|marca|cambia|pasa|deja)\\s+(?:el\\s+)?(?:proyecto\\s+)?(\\p{L}+)\\s+(?:(?:a|como)\\s+)?"
					+ "(en riesgo|pausado|en pausa|completado|terminado|cancelado|en curso|planificaci[oó]n)", FLAGS);
	/** "sube la prioridad de Nexo a 5" */
	private static final Pattern PRIORIDAD_PROYECTO = Pattern.compile(
			"prioridad\\s+(?:de|del|del proyecto)\\s+(\\p{L}+)\\s+a\\s+([1-5])", FLAGS);
	/** "añade a Hugo Benítez al proyecto Orión como desarrollador backend" */
	private static final Pattern MIEMBRO = Pattern.compile(
			"a[nñ]ade\\s+a\\s+(.+?)\\s+al\\s+(?:equipo\\s+(?:de|del proyecto)\\s+|proyecto\\s+)(\\p{L}+)(?:\\s+como\\s+(.+?))?(?=[.;!?\\n]|$)", FLAGS);
	/** "riesgo en Atlas: el proveedor no llega" */
	private static final Pattern DIARIO = Pattern.compile(
			"(riesgo|decisi[oó]n|bloqueo)\\s+en\\s+(?:el\\s+proyecto\\s+)?(\\p{L}+)\\s*:\\s*(.+?)(?=\\n|$)", FLAGS);
	/** "marca la #303 como hecha" */
	private static final Pattern ESTADO_TAREA = Pattern.compile(
			"#(\\d+)\\D{0,25}?\\b(?:como|a|en)\\s+(hecha|terminada|completada|en curso|pendiente)", FLAGS);
	/** "he terminado la #303" */
	private static final Pattern TAREA_TERMINADA = Pattern.compile(
			"(?:he\\s+terminado|termin[eé]|he\\s+acabado|acab[eé])\\s+(?:la\\s+)?(?:tarea\\s+)?#(\\d+)", FLAGS);
	/** "#301 bloquea (a) #302" */
	private static final Pattern BLOQUEA = Pattern.compile("#(\\d+)\\D{0,30}?\\bbloquea\\b\\D{0,20}?#(\\d+)", FLAGS);
	/** "#301 es parte de / es necesaria para / va antes de #302" */
	private static final Pattern NECESARIA_PARA = Pattern.compile(
			"#(\\d+)\\D{0,30}?\\b(?:es parte de|forma parte de|es necesari[oa] para|va antes de|hace falta para)\\D{0,20}?#(\\d+)", FLAGS);

	public ExtraccionDto extraer(String mensaje, ContextoChat ctx) {
		String proyecto = proyecto(mensaje, ctx);
		int urgencia = urgencia(mensaje);
		String subtareaDe = grupo(SUBTAREA_DE, mensaje, 1);

		List<TareaPropiaExtraidaDto> tareas = new ArrayList<>();
		Matcher mt = TAREA_PROPIA.matcher(mensaje);
		while (mt.find()) {
			String titulo = capitalizar(mt.group(2));
			if (titulo.length() >= 3) {
				tareas.add(new TareaPropiaExtraidaDto(titulo, null, urgencia, proyecto, subtareaDe));
			}
		}

		List<PeticionExtraidaDto> peticiones = new ArrayList<>();
		if (tareas.isEmpty()) {
			Matcher mp = PETICION.matcher(mensaje);
			while (mp.find()) {
				String objeto = limpiarObjeto(mp.group(1));
				if (objeto.length() >= 3) {
					peticiones.add(new PeticionExtraidaDto(tipo(objeto), null, objeto, proyecto, urgencia, subtareaDe));
				}
			}
		}

		List<MemoriaExtraidaDto> memorias = new ArrayList<>();
		Matcher mw = TRABAJANDO_EN.matcher(mensaje);
		if (mw.find()) {
			memorias.add(new MemoriaExtraidaDto(TipoMemoria.PROYECTO_ACTUAL,
					"Está trabajando en " + mw.group(1).trim() + ".", proyecto));
		}
		Matcher md = DECISION.matcher(mensaje);
		if (md.find()) {
			memorias.add(new MemoriaExtraidaDto(TipoMemoria.DECISION, "Ha decidido " + md.group(1).trim() + ".", proyecto));
		}
		List<DependenciaExtraidaDto> dependencias = new ArrayList<>();
		Matcher mb = BLOQUEA.matcher(mensaje);
		while (mb.find()) {
			dependencias.add(new DependenciaExtraidaDto("#" + mb.group(1), "#" + mb.group(2)));
		}
		Matcher mn = NECESARIA_PARA.matcher(mensaje);
		while (mn.find()) {
			dependencias.add(new DependenciaExtraidaDto("#" + mn.group(1), "#" + mn.group(2)));
		}

		List<String> consultas = new ArrayList<>();
		if (CONSULTA.matcher(mensaje).find()) {
			if (MIS_PROYECTOS.matcher(mensaje).find()) {
				consultas.add("*");
			} else {
				proyectosMencionados(mensaje, ctx).forEach(consultas::add);
			}
		}

		List<CambioProyectoDto> cambiosProyecto = new ArrayList<>();
		Matcher me = ESTADO_PROYECTO.matcher(mensaje);
		while (me.find()) {
			conocido(me.group(1), ctx).ifPresent(nombre ->
					cambiosProyecto.add(new CambioProyectoDto("cambiar_estado", nombre, me.group(2), null, null, null, null)));
		}
		Matcher mpr = PRIORIDAD_PROYECTO.matcher(mensaje);
		while (mpr.find()) {
			conocido(mpr.group(1), ctx).ifPresent(nombre ->
					cambiosProyecto.add(new CambioProyectoDto("cambiar_prioridad", nombre, mpr.group(2), null, null, null, null)));
		}
		Matcher mm = MIEMBRO.matcher(mensaje);
		while (mm.find()) {
			conocido(mm.group(2), ctx).ifPresent(nombre -> cambiosProyecto.add(new CambioProyectoDto("anadir_miembro", nombre,
					null, mm.group(1).trim(), mm.group(3) == null ? null : mm.group(3).trim(), null, null)));
		}
		Matcher mdiario = DIARIO.matcher(mensaje);
		while (mdiario.find()) {
			String tipo = Texto.normalizar(mdiario.group(1));
			String accion = tipo.startsWith("riesgo") ? "registrar_riesgo" : tipo.startsWith("bloqueo") ? "registrar_bloqueo" : "registrar_decision";
			conocido(mdiario.group(2), ctx).ifPresent(nombre ->
					cambiosProyecto.add(new CambioProyectoDto(accion, nombre, mdiario.group(3).trim(), null, null, null, null)));
		}

		List<CambioTareaDto> cambiosTarea = new ArrayList<>();
		Matcher mtarea = ESTADO_TAREA.matcher(mensaje);
		while (mtarea.find()) {
			cambiosTarea.add(new CambioTareaDto("#" + mtarea.group(1), mtarea.group(2), null, null, null));
		}
		Matcher mterm = TAREA_TERMINADA.matcher(mensaje);
		while (mterm.find()) {
			cambiosTarea.add(new CambioTareaDto("#" + mterm.group(1), "hecha", null, null, null));
		}
		return new ExtraccionDto(peticiones, tareas, memorias, dependencias, consultas, cambiosProyecto, cambiosTarea);
	}

	private static List<String> proyectosMencionados(String mensaje, ContextoChat ctx) {
		String normalizado = " " + Texto.normalizar(mensaje) + " ";
		return ctx.proyectosConocidos().stream().filter(p -> normalizado.contains(" " + Texto.normalizar(p) + " ")).toList();
	}

	private static java.util.Optional<String> conocido(String nombre, ContextoChat ctx) {
		String n = Texto.normalizar(nombre);
		return ctx.proyectosConocidos().stream().filter(p -> Texto.normalizar(p).equals(n)).findFirst();
	}

	/** Proyecto nombrado explícitamente ("proyecto X") o un proyecto conocido que aparezca en el mensaje. */
	private String proyecto(String mensaje, ContextoChat ctx) {
		Matcher m = PROYECTO.matcher(mensaje);
		if (m.find()) {
			String nombre = m.group(1);
			return ctx.proyectosConocidos().stream()
					.filter(p -> Texto.normalizar(p).equals(Texto.normalizar(nombre)))
					.findFirst().orElse(capitalizar(nombre));
		}
		String normalizado = " " + Texto.normalizar(mensaje) + " ";
		return ctx.proyectosConocidos().stream()
				.filter(p -> normalizado.contains(" " + Texto.normalizar(p) + " "))
				.findFirst().orElse(null);
	}

	private static int urgencia(String mensaje) {
		if (URGENTE.matcher(mensaje).find()) return 5;
		if (PRONTO.matcher(mensaje).find()) return 4;
		if (SIN_PRISA.matcher(mensaje).find()) return 2;
		return 3;
	}

	private static TipoPeticion tipo(String objeto) {
		String n = Texto.normalizar(objeto);
		if (n.contains("licencia") || n.contains("suscripcion")) return TipoPeticion.LICENCIA;
		if (n.contains("acceso") || n.contains("permiso") || n.contains("vpn") || n.contains("credencial") || n.contains("usuario en")) return TipoPeticion.ACCESO;
		if (n.contains("informacion") || n.contains("informe") || n.contains("datos") || n.contains("saber") || n.contains("certificado")) return TipoPeticion.INFORMACION;
		return TipoPeticion.OTRO;
	}

	private static String limpiarObjeto(String objeto) {
		String o = objeto.trim()
				.replaceFirst("(?i)^(que (?:me|nos) (?:den|deis|des|dejen|pasen|activen|habiliten)\\s+)", "")
				.replaceFirst("(?i)^(un|una|unos|unas|el|la|los|las|mi|mis)\\s+", "");
		return o.replaceAll("\\s+", " ");
	}

	private static String grupo(Pattern p, String texto, int grupo) {
		Matcher m = p.matcher(texto);
		return m.find() ? m.group(grupo).trim() : null;
	}

	private static String capitalizar(String s) {
		String t = s.trim();
		return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
	}
}
