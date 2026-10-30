// Cliente mínimo de la API REST del backend.

// Usuario del login simulado: se manda en cada petición para que los cambios queden con su autor.
let empleadoId = null
export function usarEmpleado(id) {
  empleadoId = id
}

async function request(method, path, body) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (empleadoId) headers['X-Empleado-Id'] = String(empleadoId)
  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    // El backend devuelve ProblemDetail: { title, detail, status }
    throw new Error(data?.detail || data?.title || `Error HTTP ${res.status}`)
  }
  return data
}

export const api = {
  empleados: (q) => request('GET', `/empleados${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  tareasDe: (id, incluirHechas) => request('GET', `/empleados/${id}/tareas?incluirHechas=${incluirHechas}`),
  peticionesDe: (id) => request('GET', `/empleados/${id}/peticiones`),
  memoriaDe: (id) => request('GET', `/empleados/${id}/memoria`),
  actualizarTarea: (id, cambios) => request('PATCH', `/tareas/${id}`, cambios),
  dependenciasDe: (id) => request('GET', `/tareas/${id}/dependencias`),
  recalcular: () => request('POST', '/priorizacion/recalcular'),
  grafo: (filtro = {}) => request('GET', '/grafo?' + new URLSearchParams(
    Object.entries(filtro).filter(([, v]) => v != null && v !== false).map(([k, v]) => [k, String(v)]))),
  proyectos: () => request('GET', '/proyectos'),
  proyecto: (id) => request('GET', `/proyectos/${id}`),
  crearProyecto: (p) => request('POST', '/proyectos', p),
  actualizarProyecto: (id, cambios) => request('PATCH', `/proyectos/${id}`, cambios),
  guardarMiembro: (id, miembro) => request('PUT', `/proyectos/${id}/miembros`, miembro),
  quitarMiembro: (id, empleado) => request('DELETE', `/proyectos/${id}/miembros/${empleado}`),
  crearHito: (id, hito) => request('POST', `/proyectos/${id}/hitos`, hito),
  actualizarHito: (hitoId, cambios) => request('PATCH', `/hitos/${hitoId}`, cambios),
  crearNota: (id, nota) => request('POST', `/proyectos/${id}/notas`, nota),
  actualizarNota: (notaId, cambios) => request('PATCH', `/notas/${notaId}`, cambios),
  crearTarea: (t) => request('POST', '/tareas', t),
  dominios: () => request('GET', '/dominios'),
  historial: (id) => request('GET', `/chat/${id}/mensajes`),
  enviarMensaje: (id, mensaje) => request('POST', `/chat/${id}/mensajes`, { mensaje }),
  estadoIa: () => request('GET', '/ia/estado'),
  archivarMemoria: (id) => request('PATCH', `/memoria/${id}/archivar`),
}
