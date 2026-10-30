import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { DEPARTAMENTOS } from '../util.js'
import { Cargando } from './Comunes.jsx'

const GRUPOS = [
  ['LICENCIA', 'Licencias'],
  ['ACCESO', 'Accesos'],
  ['INFORMACION', 'Información'],
  ['OTRO', 'Otras gestiones'],
]

export default function Directorio() {
  const [dominios, setDominios] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.dominios().then(setDominios).catch((e) => setError(e.message))
  }, [])

  return (
    <div className="vista">
      <header className="vista-cabecera">
        <div>
          <h1 className="titulo-vista">Quién hace qué</h1>
          <p className="lead">
            A quién asigna el asistente cada petición. Si un área tiene varias personas, la tarea va a quien tenga menos
            trabajo abierto. Las palabras clave sirven para reconocer la petición cuando la IA no propone un área.
          </p>
        </div>
      </header>

      {error && <div className="aviso aviso-error" role="alert">{error}</div>}
      {!dominios && !error && <Cargando />}

      {dominios && GRUPOS.map(([tipo, titulo]) => {
        const lista = dominios.filter((d) => d.tipoPeticion === tipo)
        if (lista.length === 0) return null
        return (
          <section key={tipo} className="grupo-directorio">
            <h2 className="titulo-seccion">{titulo}</h2>
            <div className="panel tabla-desplazable">
              <table className="tabla">
                <thead>
                  <tr>
                    <th style={{ width: '32%' }}>Área</th>
                    <th style={{ width: '30%' }}>Responsables</th>
                    <th>Se reconoce por</th>
                  </tr>
                </thead>
                <tbody>
                  {lista.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <span className="fuerte">{d.nombre}</span>
                        <div className="texto-2 pequeno">{d.descripcion}</div>
                      </td>
                      <td>
                        <ul>
                          {d.responsables.map((r) => (
                            <li key={r.id}>{r.nombre} <span className="texto-3 pequeno">{DEPARTAMENTOS[r.departamento]}</span></li>
                          ))}
                        </ul>
                      </td>
                      <td className="palabras">{d.palabrasClave?.split(',').join(', ')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )
      })}
    </div>
  )
}
