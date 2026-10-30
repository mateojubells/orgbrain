import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { DEPARTAMENTOS } from '../util.js'
import { Marca } from './Comunes.jsx'

// Personajes de los escenarios de demo creados por el seeder.
const DESTACADOS = [
  { nombre: 'Ana López García', motivo: 'Jefa de los proyectos Orión y Lince. El asistente ya tiene contexto sobre ella.' },
  { nombre: 'Laura Martín Sánchez', motivo: 'Gestiona las licencias de Microsoft 365. Una tarea suya de urgencia 2 frena al equipo de Orión.' },
  { nombre: 'Javier Ortega Ruiz', motivo: 'Su certificado SSL, de urgencia 1, bloquea el lanzamiento del portal Atlas.' },
  { nombre: 'Tomás Aguilar Peña', motivo: 'Dirige la PMO: Atlas, en riesgo, y la migración Fénix, con un hito vencido.' },
]

export default function Login({ onEntrar }) {
  const [empleados, setEmpleados] = useState([])
  const [filtro, setFiltro] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    api.empleados().then(setEmpleados).catch((e) => setError(e.message))
  }, [])

  const porDepartamento = useMemo(() => {
    const f = filtro.trim().toLowerCase()
    const grupos = {}
    for (const e of empleados) {
      if (f && !`${e.nombre} ${e.rol}`.toLowerCase().includes(f)) continue
      ;(grupos[e.departamento] ||= []).push(e)
    }
    return grupos
  }, [empleados, filtro])

  const destacados = DESTACADOS
    .map((d) => ({ ...d, empleado: empleados.find((e) => e.nombre === d.nombre) }))
    .filter((d) => d.empleado)

  return (
    <div className="acceso">
      <div className="acceso-intro">
        <Marca />
        <h1 className="titulo-vista">Entrar como</h1>
        <p className="texto-2">
          Prototipo con 100 empleados simulados y sin contraseñas. Elige una persona para ver su cola de trabajo,
          sus proyectos y su asistente.
        </p>
      </div>

      {error && (
        <div className="aviso aviso-error" role="alert">
          No se pudo cargar la lista de empleados: {error}. Comprueba que el backend está arrancado en el puerto 8080.
        </div>
      )}

      {destacados.length > 0 && (
        <section>
          <h2 className="titulo-seccion" style={{ marginBottom: 6 }}>Para una demostración</h2>
          <ul className="demo">
            {destacados.map(({ empleado, motivo }) => (
              <li key={empleado.id}>
                <button className="enlace" onClick={() => onEntrar(empleado)}>{empleado.nombre}</button>
                <span className="texto-2">{motivo}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <div className="vista-cabecera" style={{ marginBottom: 14 }}>
          <h2 className="titulo-seccion">Toda la plantilla</h2>
          <input className="campo" style={{ maxWidth: 280 }} type="search" placeholder="Buscar por nombre o rol"
            value={filtro} onChange={(e) => setFiltro(e.target.value)} aria-label="Buscar empleado" />
        </div>
        <div className="directorio-acceso">
          {Object.entries(DEPARTAMENTOS).map(([clave, etiqueta]) =>
            porDepartamento[clave]?.length ? (
              <div key={clave}>
                <h3>{etiqueta} <span className="texto-3">{porDepartamento[clave].length}</span></h3>
                <ul>
                  {porDepartamento[clave].map((e) => (
                    <li key={e.id}>
                      <button className="persona" onClick={() => onEntrar(e)}>
                        <span>{e.nombre}</span>
                        <span className="rol">{e.rol}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null,
          )}
        </div>
      </section>
    </div>
  )
}
