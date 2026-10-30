package com.orgbrain.util;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilidades de texto para la búsqueda por palabras clave (v1 sin embeddings). */
public final class Texto {

	private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
	private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9 ]+");

	private static final Set<String> STOPWORDS = Set.of(
			"el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a", "en", "y", "o", "que",
			"por", "para", "con", "sin", "sobre", "mi", "mis", "tu", "tus", "su", "sus", "me", "te", "se", "nos",
			"lo", "le", "les", "es", "son", "esta", "estan", "estoy", "este", "esto", "eso", "ese", "esa", "hay",
			"muy", "mas", "pero", "como", "cuando", "donde", "ya", "yo", "no", "si", "tambien", "hola", "gracias",
			"necesito", "falta", "hace", "tengo", "tener", "quiero", "puedes", "podrias", "favor", "algo", "todo",
			"proyecto", "tarea", "tareas", "ahora", "hoy", "manana", "semana", "cosa", "cosas", "bien", "vale",
			"ser", "estar", "haber", "hacer", "he", "ha", "han", "fue", "sea", "porque", "entre", "desde", "hasta");

	private Texto() {
	}

	/** Minúsculas, sin acentos ni signos. */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinAcentos = DIACRITICOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
		return NO_ALFANUMERICO.matcher(sinAcentos.toLowerCase(Locale.ROOT)).replaceAll(" ").replaceAll("\\s+", " ").trim();
	}

	/** Palabras significativas (≥ 3 letras, sin stopwords), en orden y sin repetir. */
	public static Set<String> tokens(String texto) {
		Set<String> resultado = new LinkedHashSet<>();
		for (String palabra : normalizar(texto).split(" ")) {
			if (palabra.length() >= 3 && !STOPWORDS.contains(palabra)) {
				resultado.add(palabra);
			}
		}
		return resultado;
	}

	/** Lista separada por comas, recortada para caber en VARCHAR(500). */
	public static String unirPalabrasClave(Set<String> palabras) {
		String unido = String.join(",", palabras);
		if (unido.length() <= 500) {
			return unido;
		}
		return unido.substring(0, unido.lastIndexOf(',', 500));
	}

	private static final Pattern CARACTER_CORRUPTO = Pattern.compile("[\\n\\r\\t\\uFFFD]");

	/**
	 * Repara letras perdidas en texto generado por el LLM. A veces la salida estructurada de Gemini sustituye
	 * letras acentuadas por un salto de línea ("gu\n de uso" en lugar de "guía de uso"). Se busca en el texto
	 * original del usuario la palabra con el mismo principio y final para recuperar las letras; si no aparece,
	 * se quita el carácter para no guardar basura.
	 */
	public static String repararLetras(String generado, String fuente) {
		if (generado == null || !CARACTER_CORRUPTO.matcher(generado).find()) {
			return generado;
		}
		StringBuilder resultado = new StringBuilder();
		Matcher m = CARACTER_CORRUPTO.matcher(generado);
		int ultimo = 0;
		while (m.find()) {
			String antes = generado.substring(ultimo, m.start());
			resultado.append(antes);
			String prefijo = antes.replaceAll("(?s)^.*[^\\p{L}]", "");
			String resto = generado.substring(m.end());
			String sufijo = resto.replaceAll("(?s)^(\\p{L}*[ ]?\\p{L}{0,3}).*$", "$1");
			String letras = null;
			if (fuente != null && (prefijo.length() >= 2 || sufijo.strip().length() >= 2)) {
				Matcher enFuente = Pattern.compile(
								"(?iu)(?<!\\p{L})" + Pattern.quote(prefijo) + "(\\p{L}{1,3})" + Pattern.quote(sufijo)
										+ (sufijo.isEmpty() || !Character.isLetter(sufijo.charAt(sufijo.length() - 1)) ? "(?!\\p{L})" : ""))
						.matcher(fuente);
				if (enFuente.find()) {
					letras = enFuente.group(1);
				}
			}
			resultado.append(letras != null ? letras : "");
			ultimo = m.end();
		}
		resultado.append(generado.substring(ultimo));
		return resultado.toString();
	}

	/**
	 * Coincidencia tolerante entre palabras normalizadas: igualdad, una es prefijo de la otra
	 * ("licencias" ~ "licencia") o comparten raíz larga ("automatizar" ~ "automatizacion").
	 */
	public static boolean coincide(String a, String b) {
		if (a.equals(b)) {
			return true;
		}
		if (a.length() < 4 || b.length() < 4) {
			return false;
		}
		if (a.startsWith(b) || b.startsWith(a)) {
			return true;
		}
		int prefijo = 0;
		int max = Math.min(a.length(), b.length());
		while (prefijo < max && a.charAt(prefijo) == b.charAt(prefijo)) {
			prefijo++;
		}
		return prefijo >= 6 && prefijo >= 0.8 * max;
	}
}
