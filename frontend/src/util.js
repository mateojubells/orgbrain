export const DEPARTAMENTOS = {
  IT: 'IT',
  RRHH: 'RRHH',
  MARKETING: 'Marketing',
  VENTAS: 'Ventas',
  FINANZAS: 'Finanzas',
  DIRECCION_PROYECTO: 'Dirección de proyecto',
}

export const ESTADOS = {
  PENDIENTE: 'Pendiente',
  EN_CURSO: 'En curso',
  HECHA: 'Hecha',
}

export const TIPOS_MEMORIA = {
  PROYECTO_ACTUAL: 'Proyecto actual',
  NECESIDAD_PENDIENTE: 'Necesidad pendiente',
  DECISION: 'Decisión',
  NOTA_GENERAL: 'Nota',
}

export const ESTADOS_PROYECTO = {
  PLANIFICACION: 'Planificación',
  EN_CURSO: 'En curso',
  EN_RIESGO: 'En riesgo',
  PAUSADO: 'Pausado',
  COMPLETADO: 'Completado',
  CANCELADO: 'Cancelado',
}

export const TIPOS_NOTA = {
  DECISION: 'Decisión',
  RIESGO: 'Riesgo',
  BLOQUEO: 'Bloqueo',
  NOTA: 'Nota',
  CAMBIO: 'Cambio',
}

/** Salud del proyecto → texto y color de señal. */
export const SALUD = {
  VERDE: { texto: 'En marcha', senal: 'verde' },
  AMARILLO: { texto: 'Atención', senal: 'ocre' },
  ROJO: { texto: 'En problemas', senal: 'carmin' },
}

/**
 * Color de señal para una urgencia calculada (1-10). Es la única forma en que la urgencia se colorea en la app:
 * neutro por debajo de 3, ocre de 3 a 4, óxido de 4 a 5, carmín de 5 en adelante.
 */
export function senalUrgencia(u) {
  if (u >= 5) return 'carmin'
  if (u >= 4) return 'oxido'
  if (u >= 3) return 'ocre'
  return 'neutro'
}

/** Cifra con coma decimal y hasta 2 decimales: 6.4 → "6,4", 5 → "5". */
export function formatoUrgencia(u) {
  return u.toLocaleString('es-ES', { maximumFractionDigits: 2 })
}

export function subio(tarea) {
  return tarea.urgenciaCalculada > tarea.urgenciaPropia + 0.001
}

export function fechaCorta(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })
}

export function horaCorta(iso) {
  return new Date(iso).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
}

export function plural(n, singular, pluralForm = `${singular}s`) {
  return `${n} ${n === 1 ? singular : pluralForm}`
}

export function diasHasta(iso) {
  if (!iso) return null
  const hoy = new Date()
  hoy.setHours(0, 0, 0, 0)
  return Math.round((new Date(iso) - hoy) / 86400000)
}

export function vencida(tarea) {
  return tarea.fechaLimite && tarea.estado !== 'HECHA' && diasHasta(tarea.fechaLimite) < 0
}
