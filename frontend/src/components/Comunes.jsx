import { useLayoutEffect, useRef } from 'react'
import { ESTADOS, formatoUrgencia, senalUrgencia, subio } from '../util.js'

const SEGMENTOS = 10

/**
 * Regla de urgencia: 10 segmentos. Relleno liso hasta la urgencia propia y trama diagonal de la propia a la
 * calculada: la trama es la urgencia heredada de las tareas que dependen de esta.
 */
export function Regla({ propia, calculada }) {
  const hasta = Math.min(SEGMENTOS, Math.round(calculada))
  const senal = senalUrgencia(calculada)
  return (
    <span className="regla" data-senal={senal} role="img"
      aria-label={`Urgencia calculada ${formatoUrgencia(calculada)} de 10; propia ${propia}`}>
      {Array.from({ length: SEGMENTOS }, (_, i) => {
        const n = i + 1
        const tipo = n <= propia ? 'propia' : n <= hasta ? 'heredada' : 'vacio'
        return <span key={n} className={`regla-seg regla-${tipo}`} />
      })}
    </span>
  )
}

/** Cifra de urgencia calculada con su color de señal. {@code propia} opcional para mostrar "2 → 6,4". */
export function Urgencia({ calculada, propia }) {
  const sube = propia != null && calculada > propia + 0.001
  return (
    <span className="urgencia" data-senal={senalUrgencia(calculada)}>
      {sube && <span className="urgencia-propia">{propia} →</span>}
      <span className="cifra">{formatoUrgencia(calculada)}</span>
    </span>
  )
}

/** Estado de una tarea por su forma: círculo vacío, medio o lleno. */
export function Estado({ estado, conTexto = true }) {
  return (
    <span className="estado" data-estado={estado}>
      <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
        <circle cx="6" cy="6" r="4.75" fill="none" stroke="currentColor" strokeWidth="1.5" />
        {estado === 'EN_CURSO' && <path d="M6 1.25 A4.75 4.75 0 0 1 6 10.75 Z" fill="currentColor" />}
        {estado === 'HECHA' && <circle cx="6" cy="6" r="4.75" fill="currentColor" />}
      </svg>
      {conTexto && <span>{ESTADOS[estado]}</span>}
    </span>
  )
}

/** Punto de señal de 8 px (salud de proyecto, estado de la IA). */
export function Punto({ senal, titulo }) {
  return <span className="punto" data-senal={senal} title={titulo} aria-hidden={titulo ? undefined : 'true'} />
}

/** Motivo de la subida de prioridad, en texto y junto a la cifra. */
export function Motivo({ tarea }) {
  if (!subio(tarea) || !tarea.motivoUrgencia) return null
  return (
    <p className="motivo">
      <svg width="9" height="9" viewBox="0 0 10 10" aria-hidden="true"><path d="M5 1 L9 8.5 H1 Z" fill="currentColor" /></svg>
      {/* El motor une las causas con " · "; aquí se leen como frases. */}
      {tarea.motivoUrgencia.split(' · ').join('. ')}.
    </p>
  )
}

export function Cargando({ texto = 'Cargando…' }) {
  return <div className="cargando" role="status">{texto}</div>
}

/** Marca: dos nodos unidos, el segundo en petróleo. */
export function Marca() {
  return (
    <span className="marca">
      <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
        <circle cx="5" cy="14" r="3" fill="currentColor" />
        <path d="M7.5 12.5 L12.5 7.5" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="15" cy="5" r="3" className="marca-acento" />
      </svg>
      OrgBrain
    </span>
  )
}

/**
 * Animación FLIP para listas reordenadas: cuando cambia el orden (p. ej. tras recalcular prioridades) cada fila se
 * desliza de su posición anterior a la nueva. Solo se mueve en respuesta a esa acción del usuario.
 */
export function useReordenAnimado(claves, activo) {
  const contenedor = useRef(null)
  const previas = useRef(new Map())

  useLayoutEffect(() => {
    const el = contenedor.current
    if (!el) return
    const filas = [...el.querySelectorAll('[data-clave]')]
    const reducido = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (activo && !reducido) {
      filas.forEach((f) => {
        const antes = previas.current.get(f.dataset.clave)
        if (antes == null) return
        const delta = antes - f.getBoundingClientRect().top
        if (Math.abs(delta) < 1) return
        f.animate([{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0)' }],
          { duration: 220, easing: 'cubic-bezier(.2,.7,.2,1)' })
      })
    }
    previas.current = new Map(filas.map((f) => [f.dataset.clave, f.getBoundingClientRect().top]))
  }, [claves, activo])

  return contenedor
}
