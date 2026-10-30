import { useCallback, useEffect, useRef, useState } from 'react'
import { SendHorizontal } from 'lucide-react'
import { api } from '../api.js'
import { TIPOS_MEMORIA, fechaCorta, horaCorta, plural } from '../util.js'
import { Cargando, Urgencia } from './Comunes.jsx'

const SUGERENCIAS = [
  'Estoy desarrollando el proyecto Orión y me falta la licencia de Copilot para el equipo',
  'Necesito acceso a la VPN para poder trabajar desde casa esta semana',
  'Créame una subtarea para preparar las diapositivas de la demo dentro del proyecto Orión',
  'Hemos decidido retrasar el lanzamiento de Lince hasta después del verano',
  '¿Qué tengo más urgente?',
  '¿Cómo va el proyecto Orión?',
  'Pon Atlas en riesgo y anota el riesgo: el certificado SSL caduca antes del go-live',
]

const MODOS = {
  GEMINI: 'Procesado con Gemini',
  SIN_IA: 'Procesado con reglas (sin IA)',
  RESPALDO_SIN_IA: 'Gemini no respondió: procesado con reglas',
}

// Orden de los grupos en el panel de memoria.
const GRUPOS_MEMORIA = ['DECISION', 'NECESIDAD_PENDIENTE', 'PROYECTO_ACTUAL', 'NOTA_GENERAL']
const TITULOS_GRUPO = {
  DECISION: 'Decisiones',
  NECESIDAD_PENDIENTE: 'Necesidades pendientes',
  PROYECTO_ACTUAL: 'Proyectos en los que estás',
  NOTA_GENERAL: 'Notas',
}

export default function Chat({ usuario, onCambios }) {
  const [mensajes, setMensajes] = useState(null)
  const [memoria, setMemoria] = useState([])
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const finRef = useRef(null)
  const entradaRef = useRef(null)

  const cargarMemoria = useCallback(() => api.memoriaDe(usuario.id).then(setMemoria), [usuario.id])

  useEffect(() => {
    setMensajes(null)
    api.historial(usuario.id).then(setMensajes).catch((e) => setError(e.message))
    cargarMemoria().catch(() => {})
  }, [usuario.id, cargarMemoria])

  useEffect(() => {
    finRef.current?.scrollIntoView({ block: 'end' })
  }, [mensajes, enviando])

  const enviar = async (contenido) => {
    const mensaje = (contenido ?? texto).trim()
    if (!mensaje || enviando) return
    setTexto('')
    setError(null)
    setEnviando(true)
    setMensajes((m) => [...(m || []), { id: `local-${Date.now()}`, rol: 'USUARIO', mensaje, fechaHora: new Date().toISOString() }])
    try {
      const r = await api.enviarMensaje(usuario.id, mensaje)
      setMensajes((m) => [...m, {
        id: `resp-${Date.now()}`, rol: 'ASISTENTE', mensaje: r.respuesta, fechaHora: new Date().toISOString(), detalle: r,
      }])
      if (r.tareasCreadas.length > 0 || r.tareasActualizadas.length > 0 || r.proyectosAfectados.length > 0) onCambios()
      cargarMemoria().catch(() => {})
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
      entradaRef.current?.focus()
    }
  }

  const archivar = async (id) => {
    await api.archivarMemoria(id)
    cargarMemoria()
  }

  const activas = memoria.filter((m) => m.activa)
  const archivadas = memoria.filter((m) => !m.activa)

  return (
    <div className="vista">
      <header className="vista-cabecera">
        <div>
          <h1 className="titulo-vista">Asistente</h1>
          <p className="lead">
            Cuéntale lo que necesitas o en qué trabajas. Las peticiones van al backlog de quien las gestiona y quedan
            enlazadas con tu trabajo; también responde cómo va un proyecto y aplica cambios.
          </p>
        </div>
      </header>

      <div className="asistente">
        <section className="panel conversacion" aria-label="Conversación">
          <div className="transcripcion">
            {!mensajes && !error && <Cargando />}
            {mensajes?.length === 0 && (
              <div className="bienvenida">
                <p className="subtitulo">Aún no habéis hablado.</p>
                <p>Prueba con una de las sugerencias de abajo, o escribe algo como «necesito acceso al CRM para el proyecto Nexo».</p>
              </div>
            )}
            {mensajes?.map((m) => <Turno key={m.id} mensaje={m} usuario={usuario} />)}
            {enviando && (
              <div className="turno turno-asistente">
                <div className="turno-cabecera"><strong>OrgBrain</strong></div>
                <p className="escribiendo" aria-label="Procesando">Procesando<span>.</span><span>.</span><span>.</span></p>
              </div>
            )}
            <div ref={finRef} />
          </div>

          <div className="compositor">
            {error && <div className="aviso aviso-error" role="alert" style={{ margin: 0 }}>{error}</div>}
            <details className="sugerencias">
              <summary>Sugerencias</summary>
              <ul>
                {SUGERENCIAS.map((s) => (
                  <li key={s}><button onClick={() => enviar(s)} disabled={enviando}>{s}</button></li>
                ))}
              </ul>
            </details>
            <form className="entrada" onSubmit={(e) => { e.preventDefault(); enviar() }}>
              <textarea ref={entradaRef} className="campo" rows={2} value={texto}
                placeholder="Escribe un mensaje. Intro envía; Mayús+Intro hace un salto de línea."
                onChange={(e) => setTexto(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    enviar()
                  }
                }}
                aria-label="Mensaje para el asistente" />
              <button className="btn btn-primario" type="submit" disabled={enviando || !texto.trim()}>
                <SendHorizontal size={14} strokeWidth={1.75} aria-hidden="true" /> Enviar
              </button>
            </form>
          </div>
        </section>

        <aside className="panel memoria" aria-label="Memoria del asistente">
          <div className="panel-cabecera">
            <h2 className="titulo-seccion">Lo que recuerdo de ti</h2>
          </div>
          <div className="panel-cuerpo">
            <p className="texto-2 pequeno" style={{ marginBottom: 14 }}>
              Se usa como contexto en cada mensaje. Archiva lo que ya no esté vigente.
            </p>
            {activas.length === 0 && <p className="texto-2">Todavía no me has contado nada.</p>}
            {GRUPOS_MEMORIA.map((tipo) => {
              const items = activas.filter((m) => m.tipo === tipo)
              if (items.length === 0) return null
              return (
                <section key={tipo} className="memoria-grupo">
                  <h3>{TITULOS_GRUPO[tipo]}</h3>
                  <ul>
                    {items.map((m) => (
                      <li key={m.id}>
                        <p>{m.contenido}</p>
                        <div className="pie">
                          <span>{m.proyectoRelacionado ? `${m.proyectoRelacionado}, ` : ''}{fechaCorta(m.fecha)}</span>
                          <button className="enlace pequeno" onClick={() => archivar(m.id)}>Archivar</button>
                        </div>
                      </li>
                    ))}
                  </ul>
                </section>
              )
            })}
            {archivadas.length > 0 && (
              <details className="memoria-grupo">
                <summary className="leyenda" style={{ cursor: 'pointer' }}>{plural(archivadas.length, 'recuerdo archivado', 'recuerdos archivados')}</summary>
                <ul>
                  {archivadas.map((m) => (
                    <li key={m.id} className="memoria-archivada">
                      <p>{m.contenido}</p>
                      <div className="pie"><span>{TIPOS_MEMORIA[m.tipo]}, {fechaCorta(m.fecha)}</span></div>
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </div>
        </aside>
      </div>
    </div>
  )
}

function Turno({ mensaje, usuario }) {
  const esUsuario = mensaje.rol === 'USUARIO'
  const d = mensaje.detalle
  return (
    <article className={`turno ${esUsuario ? 'turno-usuario' : 'turno-asistente'}`}>
      <div className="turno-cabecera">
        <strong>{esUsuario ? usuario.nombre : 'OrgBrain'}</strong>
        <time className="texto-3 pequeno" dateTime={mensaje.fechaHora}>{horaCorta(mensaje.fechaHora)}</time>
      </div>
      <p className="turno-texto">{mensaje.mensaje}</p>
      {d && <Recibo d={d} />}
    </article>
  )
}

/** Lo que el sistema hizo de verdad en este turno, separado de lo que redactó el modelo. */
function Recibo({ d }) {
  const filas = [
    ...d.tareasCreadas.map((t) => ({ t, accion: 'Creada' })),
    ...d.tareasActualizadas.map((t) => ({ t, accion: 'Actualizada' })),
  ]
  const hayAlgo = filas.length > 0 || d.proyectosAfectados.length > 0
  return (
    <>
      {d.avisos.length > 0 && (
        <div className="avisos-turno">
          {d.avisos.map((a) => <p key={a}>{a}</p>)}
        </div>
      )}
      <div className="recibo">
        {filas.length > 0 && (
          <div className="tabla-desplazable">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Tarea</th>
                  <th>Acción</th>
                  <th>Responsable</th>
                  <th>Proyecto</th>
                  <th className="num">Urgencia</th>
                </tr>
              </thead>
              <tbody>
                {filas.map(({ t, accion }) => (
                  <tr key={`${accion}-${t.id}`}>
                    <td>
                      <span className="id">#{t.id}</span> {t.titulo}
                      {t.tareaPadreId && <div className="texto-2 pequeno">Subtarea de #{t.tareaPadreId}</div>}
                    </td>
                    <td className="texto-2">{accion}</td>
                    <td>{t.responsable.nombre}</td>
                    <td className="texto-2">{t.proyecto?.nombre ?? '—'}</td>
                    <td className="num"><Urgencia propia={t.urgenciaPropia} calculada={t.urgenciaCalculada} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="recibo-pie" style={hayAlgo || filas.length ? undefined : { borderTop: 0 }}>
          <span>{MODOS[d.modoIa]}</span>
          {d.proyectosAfectados.length > 0 && <span>Proyectos: {d.proyectosAfectados.map((p) => p.nombre).join(', ')}</span>}
          {d.memoriasUsadas.length > 0 && (
            <span title={d.memoriasUsadas.map((m) => m.contenido).join('\n')}>
              {plural(d.memoriasUsadas.length, 'recuerdo usado', 'recuerdos usados')}
            </span>
          )}
          {d.memoriasGuardadas.length > 0 && <span>{plural(d.memoriasGuardadas.length, 'recuerdo nuevo', 'recuerdos nuevos')}</span>}
          <details>
            <summary>JSON extraído</summary>
            <pre>{JSON.stringify(d.extraccion, null, 2)}</pre>
          </details>
        </div>
      </div>
    </>
  )
}
