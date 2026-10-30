import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Plus } from 'lucide-react'
import { api } from '../api.js'
import { DEPARTAMENTOS, ESTADOS, ESTADOS_PROYECTO, SALUD, TIPOS_NOTA, diasHasta, fechaCorta, plural, vencida } from '../util.js'
import { Cargando, Estado, Motivo, Punto, Urgencia } from './Comunes.jsx'
import { Plazo } from './Proyectos.jsx'
import MapaMental from './MapaMental.jsx'
import Grafo from './Grafo.jsx'

const PESTANAS = [
  ['resumen', 'Resumen'],
  ['mapa', 'Mapa'],
  ['tareas', 'Tareas'],
  ['equipo', 'Equipo'],
  ['hitos', 'Hitos'],
  ['diario', 'Diario'],
  ['grafo', 'Dependencias'],
]

export default function ProyectoDetalle({ id, usuario, onVolver, onCambios }) {
  const [d, setD] = useState(null)
  const [pestana, setPestana] = useState('resumen')
  const [error, setError] = useState(null)
  const [version, setVersion] = useState(0)

  const cargar = useCallback(() => api.proyecto(id).then(setD).catch((e) => setError(e.message)), [id])

  useEffect(() => {
    cargar()
  }, [cargar])

  /** Toda escritura devuelve el detalle actualizado, así que basta con guardarlo. */
  const aplicar = async (promesa) => {
    setError(null)
    try {
      setD(await promesa)
      setVersion((v) => v + 1)
      onCambios?.()
    } catch (e) {
      setError(e.message)
    }
  }

  if (!d && !error) return <div className="vista"><Cargando texto="Cargando proyecto…" /></div>
  if (!d) return <div className="vista"><div className="aviso aviso-error">{error}</div></div>

  const r = d.resumen
  const dias = diasHasta(r.fechaObjetivo)
  const salud = SALUD[r.salud.nivel]

  return (
    <div className="vista">
      <button className="enlace volver" onClick={onVolver}>
        <ArrowLeft size={14} strokeWidth={1.75} aria-hidden="true" /> Proyectos
      </button>

      <header className="cabecera-proyecto">
        <div className="fila-superior">
          <div>
            <h1 className="titulo-vista">{r.nombre}</h1>
            {r.objetivo && <p className="lead">{r.objetivo}</p>}
          </div>
          <div className="vista-acciones">
            <label className="etiqueta-campo">Estado
              <select className="campo campo-compacto" value={r.estado}
                onChange={(e) => aplicar(api.actualizarProyecto(id, { estado: e.target.value }))}>
                {Object.entries(ESTADOS_PROYECTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
              </select>
            </label>
            <label className="etiqueta-campo">Prioridad
              <select className="campo campo-compacto" value={r.prioridad}
                onChange={(e) => aplicar(api.actualizarProyecto(id, { prioridad: Number(e.target.value) }))}>
                {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <label className="etiqueta-campo">Fecha objetivo
              <input className="campo campo-compacto" type="date" value={r.fechaObjetivo ?? ''}
                onChange={(e) => e.target.value && aplicar(api.actualizarProyecto(id, { fechaObjetivo: e.target.value }))} />
            </label>
          </div>
        </div>

        <p className="salud-linea" data-senal={salud.senal}>
          <Punto senal={salud.senal} />
          <strong>{salud.texto}.</strong>
          <span>{r.salud.motivos.join('. ')}.</span>
        </p>

        <div className="cifras">
          <Cifra valor={`${r.progreso} %`} etiqueta="Progreso" nota={`${d.tareasPorEstado.HECHA} de ${r.tareasTotales} tareas hechas`} />
          <Cifra valor={r.tareasAbiertas} etiqueta="Abiertas" nota={`${d.tareasPorEstado.EN_CURSO} en curso`} />
          <Cifra valor={r.tareasBloqueadas} etiqueta="Esperan a otras" nota="Bloqueadas por dependencias" alerta={r.tareasBloqueadas > 0} />
          <Cifra valor={r.tareasVencidas} etiqueta="Vencidas" nota="Fuera de plazo" alerta={r.tareasVencidas > 0} />
          <Cifra valor={r.personas} etiqueta="Personas" nota={`Responsable: ${r.responsable.nombre}`} />
          <Cifra valor={dias == null ? '—' : Math.abs(dias)} etiqueta={dias != null && dias < 0 ? 'Días de retraso' : 'Días restantes'}
            nota={r.fechaObjetivo ? `Objetivo: ${fechaCorta(r.fechaObjetivo)}` : 'Sin fecha objetivo'} alerta={dias != null && dias < 0} />
        </div>
      </header>

      {error && <div className="aviso aviso-error" role="alert">{error}</div>}

      <div className="pestanas" role="tablist" aria-label="Secciones del proyecto">
        {PESTANAS.map(([v, t]) => (
          <button key={v} role="tab" aria-selected={pestana === v} onClick={() => setPestana(v)}>{t}</button>
        ))}
      </div>

      {pestana === 'resumen' && <Resumen d={d} />}
      {pestana === 'mapa' && <MapaMental detalle={d} />}
      {pestana === 'tareas' && <Tareas d={d} aplicar={aplicar} id={id} />}
      {pestana === 'equipo' && <Equipo d={d} aplicar={aplicar} id={id} />}
      {pestana === 'hitos' && <Hitos d={d} aplicar={aplicar} id={id} />}
      {pestana === 'diario' && <Diario d={d} aplicar={aplicar} id={id} />}
      {pestana === 'grafo' && (
        <div className="grafo-embebido">
          <Grafo usuario={usuario} version={version} proyectoId={id} />
        </div>
      )}
    </div>
  )
}

function Cifra({ valor, etiqueta, nota, alerta }) {
  return (
    <div className={alerta ? 'alerta' : ''}>
      <span className="valor">{valor}</span>
      <span className="etiqueta">{etiqueta}</span>
      <span className="nota">{nota}</span>
    </div>
  )
}

function Resumen({ d }) {
  return (
    <div className="rejilla-resumen">
      <BloqueTareas titulo="Siguientes pasos" ayuda="Nada las bloquea: se pueden hacer ya." tareas={d.siguientesPasos} />
      <BloqueTareas titulo="Cuellos de botella" ayuda="Lo que más trabajo está frenando." tareas={d.cuellosDeBotella}
        extra={(t) => `Bloquea a ${plural(t.tareasBloqueadas, 'tarea')}.`} />
      <BloqueTareas titulo="Lo más prioritario" ayuda="Por urgencia calculada." tareas={d.prioridades} conMotivo />
      <section className="panel bloque-lista">
        <div className="panel-cabecera">
          <h2 className="subtitulo">Dependencias con otros proyectos</h2>
          <p>{d.dependenciasExternas.length}</p>
        </div>
        <div className="panel-cuerpo">
          {d.dependenciasExternas.length === 0
            ? <p className="texto-2">Ninguna: el proyecto no espera a nadie de fuera ni frena a otros.</p>
            : (
              <ul className="registro">
                {d.dependenciasExternas.map((x, i) => (
                  <li key={i}>
                    <p className={x.entrante ? 'vencido pequeno' : 'texto-2 pequeno'}>
                      {x.entrante ? 'Nos bloquea' : 'Bloqueamos a'}
                    </p>
                    {x.entrante ? (
                      <p><span className="id">#{x.bloqueante.id}</span> {x.bloqueante.titulo}
                        <span className="texto-2">, de {x.bloqueante.responsable.nombre}{x.proyectoBloqueante ? ` (${x.proyectoBloqueante.nombre})` : ''}</span></p>
                    ) : (
                      <p><span className="id">#{x.bloqueada.id}</span> {x.bloqueada.titulo}
                        <span className="texto-2">, de {x.bloqueada.responsable.nombre}{x.proyectoBloqueada ? ` (${x.proyectoBloqueada.nombre})` : ''}</span></p>
                    )}
                  </li>
                ))}
              </ul>
            )}
        </div>
      </section>
    </div>
  )
}

function BloqueTareas({ titulo, ayuda, tareas, extra, conMotivo }) {
  return (
    <section className="panel bloque-lista">
      <div className="panel-cabecera">
        <h2 className="subtitulo">{titulo}</h2>
        <p>{ayuda}</p>
      </div>
      <div className="panel-cuerpo">
        {tareas.length === 0 ? <p className="texto-2">Nada por aquí.</p> : (
          <ul className="registro">
            {tareas.map((t) => (
              <li key={t.id} className="fila-tarea">
                <Urgencia calculada={t.urgenciaCalculada} />
                <div>
                  <span className="titulo">{t.titulo}</span> <span className="id">#{t.id}</span>
                  <div className="meta">
                    <span>{t.responsable.nombre}</span>
                    <Estado estado={t.estado} />
                    {extra && <span className="fuerte">{extra(t)}</span>}
                  </div>
                  {conMotivo && <Motivo tarea={t} />}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function Tareas({ d, aplicar, id }) {
  const [nueva, setNueva] = useState(null)
  const columnas = [['PENDIENTE', 'Pendientes'], ['EN_CURSO', 'En curso'], ['HECHA', 'Hechas']]

  return (
    <div>
      <div className="vista-cabecera" style={{ marginBottom: 14 }}>
        <p className="texto-2">{plural(d.tareas.length, 'tarea')} en el proyecto, ordenadas por urgencia calculada.</p>
        <button className="btn" onClick={() => setNueva({ titulo: '', responsableId: d.equipo[0]?.empleado.id, urgenciaPropia: 3, fechaLimite: '' })}>
          <Plus size={14} strokeWidth={2} aria-hidden="true" /> Nueva tarea
        </button>
      </div>
      {nueva && (
        <form className="panel formulario" onSubmit={(e) => {
          e.preventDefault()
          aplicar(api.crearTarea({
            titulo: nueva.titulo,
            responsableId: Number(nueva.responsableId),
            urgenciaPropia: Number(nueva.urgenciaPropia),
            fechaLimite: nueva.fechaLimite || null,
            proyectoId: id,
          }).then(() => api.proyecto(id)))
          setNueva(null)
        }}>
          <div className="rejilla-campos">
            <label className="etiqueta-campo ancho-completo">Título
              <input className="campo" required value={nueva.titulo} onChange={(e) => setNueva({ ...nueva, titulo: e.target.value })} />
            </label>
            <label className="etiqueta-campo">Responsable
              <select className="campo" value={nueva.responsableId} onChange={(e) => setNueva({ ...nueva, responsableId: e.target.value })}>
                {d.equipo.map((m) => <option key={m.empleado.id} value={m.empleado.id}>{m.empleado.nombre}</option>)}
              </select>
            </label>
            <label className="etiqueta-campo">Urgencia propia
              <select className="campo" value={nueva.urgenciaPropia} onChange={(e) => setNueva({ ...nueva, urgenciaPropia: e.target.value })}>
                {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <label className="etiqueta-campo">Fecha límite
              <input className="campo" type="date" value={nueva.fechaLimite} onChange={(e) => setNueva({ ...nueva, fechaLimite: e.target.value })} />
            </label>
          </div>
          <div className="botones">
            <button className="btn btn-primario" type="submit">Crear tarea</button>
            <button className="btn" type="button" onClick={() => setNueva(null)}>Cancelar</button>
          </div>
        </form>
      )}

      <div className="tablero">
        {columnas.map(([estado, titulo]) => {
          const tareas = d.tareas.filter((t) => t.estado === estado)
          return (
            <section key={estado} className="tablero-columna" aria-label={titulo}>
              <h3><Estado estado={estado} conTexto={false} /> {titulo} <span className="texto-3">{tareas.length}</span></h3>
              <ul>
                {tareas.length === 0 && <li className="texto-3">Ninguna.</li>}
                {tareas.map((t) => (
                  <li key={t.id}>
                    <div className="fila-tarea" style={{ gridTemplateColumns: '38px minmax(0,1fr)' }}>
                      <Urgencia calculada={t.urgenciaCalculada} />
                      <span><span className="titulo">{t.titulo}</span> <span className="id">#{t.id}</span></span>
                    </div>
                    <div className="meta">
                      <span>{t.responsable.nombre}</span>
                      {t.bloqueantesPendientes > 0 && <span className="bloqueo">Espera a {t.bloqueantesPendientes}</span>}
                      {t.fechaLimite && <span className={vencida(t) ? 'vencido' : ''}>{fechaCorta(t.fechaLimite)}</span>}
                    </div>
                    <select className="campo campo-compacto" value={t.estado} aria-label={`Estado de la tarea ${t.id}`}
                      onChange={(e) => aplicar(api.actualizarTarea(t.id, { estado: e.target.value }).then(() => api.proyecto(id)))}>
                      {Object.entries(ESTADOS).map(([v, texto]) => <option key={v} value={v}>{texto}</option>)}
                    </select>
                  </li>
                ))}
              </ul>
            </section>
          )
        })}
      </div>
    </div>
  )
}

function Equipo({ d, aplicar, id }) {
  const [empleados, setEmpleados] = useState([])
  const [nuevo, setNuevo] = useState({ empleadoId: '', rolProyecto: '', dedicacion: 20 })

  useEffect(() => {
    api.empleados().then(setEmpleados).catch(() => {})
  }, [])

  return (
    <div style={{ display: 'grid', gap: 20 }}>
      <div className="panel tabla-desplazable">
        <table className="tabla">
          <thead>
            <tr>
              <th>Persona</th>
              <th>Papel en el proyecto</th>
              <th className="num">Dedicación</th>
              <th className="num">Abiertas aquí</th>
              <th className="num">Esperando</th>
              <th className="num">Carga total</th>
              <th><span className="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            {d.equipo.map((m) => (
              <tr key={m.empleado.id}>
                <td>
                  <span className={m.esResponsable ? 'fuerte' : ''}>{m.empleado.nombre}</span>
                  <div className="texto-2 pequeno">{m.rol}, {DEPARTAMENTOS[m.empleado.departamento]}</div>
                </td>
                <td>{m.esResponsable ? 'Responsable del proyecto' : m.rolProyecto ?? <span className="texto-3">Solo tiene tareas asignadas</span>}</td>
                <td className="num">{m.dedicacion != null ? `${m.dedicacion} %` : <span className="texto-3">—</span>}</td>
                <td className="num">{m.tareasAbiertas}</td>
                <td className={`num ${m.tareasEsperando > 0 ? 'bloqueo' : 'texto-3'}`}>{m.tareasEsperando}</td>
                <td className={`num ${m.cargaTotalAbierta > 6 ? 'fuerte' : ''}`} title="Tareas abiertas en toda la empresa">{m.cargaTotalAbierta}</td>
                <td className="num">
                  {!m.esResponsable && m.miembroFormal && (
                    <button className="enlace pequeno" onClick={() => aplicar(api.quitarMiembro(id, m.empleado.id))}>Quitar</button>
                  )}
                  {!m.miembroFormal && (
                    <button className="enlace pequeno"
                      onClick={() => aplicar(api.guardarMiembro(id, { empleadoId: m.empleado.id, rolProyecto: m.rol, dedicacion: 20 }))}>
                      Añadir al equipo
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <form className="panel formulario" style={{ marginBottom: 0 }} onSubmit={(e) => {
        e.preventDefault()
        aplicar(api.guardarMiembro(id, {
          empleadoId: Number(nuevo.empleadoId),
          rolProyecto: nuevo.rolProyecto || null,
          dedicacion: Number(nuevo.dedicacion),
        }))
        setNuevo({ empleadoId: '', rolProyecto: '', dedicacion: 20 })
      }}>
        <h2 className="subtitulo">Añadir a alguien al equipo</h2>
        <div className="rejilla-campos">
          <label className="etiqueta-campo">Persona
            <select className="campo" required value={nuevo.empleadoId} onChange={(e) => setNuevo({ ...nuevo, empleadoId: e.target.value })}>
              <option value="">Elige una persona</option>
              {empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre}, {e.rol}</option>)}
            </select>
          </label>
          <label className="etiqueta-campo">Papel en el proyecto
            <input className="campo" value={nuevo.rolProyecto} onChange={(e) => setNuevo({ ...nuevo, rolProyecto: e.target.value })} />
          </label>
          <label className="etiqueta-campo">Dedicación (%)
            <input className="campo" type="number" min="0" max="100" value={nuevo.dedicacion}
              onChange={(e) => setNuevo({ ...nuevo, dedicacion: e.target.value })} />
          </label>
        </div>
        <div className="botones">
          <button className="btn btn-primario" type="submit" disabled={!nuevo.empleadoId}>Añadir</button>
        </div>
      </form>
    </div>
  )
}

function Hitos({ d, aplicar, id }) {
  const [nuevo, setNuevo] = useState({ nombre: '', fechaObjetivo: '' })
  return (
    <div className="rejilla-resumen">
      <section className="panel">
        <div className="panel-cabecera">
          <h2 className="subtitulo">Hitos</h2>
          <Plazo proyecto={d.resumen} />
        </div>
        <div className="panel-cuerpo">
          {d.hitos.length === 0 && <p className="texto-2">Todavía no hay hitos.</p>}
          <ul className="linea-tiempo">
            {d.hitos.map((h) => (
              <li key={h.id}>
                <input type="checkbox" checked={h.completado} aria-label={`Completado: ${h.nombre}`}
                  onChange={(e) => aplicar(api.actualizarHito(h.id, { completado: e.target.checked }))} />
                <span className={`fecha pequeno ${h.vencido ? 'vencido' : 'texto-2'}`}>
                  {h.fechaObjetivo ? fechaCorta(h.fechaObjetivo) : 'Sin fecha'}
                </span>
                <span className={h.completado ? 'hecho' : ''}>
                  {h.nombre}
                  {h.vencido && <span className="vencido pequeno">, vencido</span>}
                  {h.completado && h.fechaCompletado && <span className="texto-3 pequeno">, completado el {fechaCorta(h.fechaCompletado)}</span>}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <form className="panel formulario" style={{ marginBottom: 0 }} onSubmit={(e) => {
        e.preventDefault()
        aplicar(api.crearHito(id, { nombre: nuevo.nombre, fechaObjetivo: nuevo.fechaObjetivo || null }))
        setNuevo({ nombre: '', fechaObjetivo: '' })
      }}>
        <h2 className="subtitulo">Nuevo hito</h2>
        <label className="etiqueta-campo">Nombre
          <input className="campo" required value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
        </label>
        <label className="etiqueta-campo">Fecha objetivo
          <input className="campo" type="date" value={nuevo.fechaObjetivo} onChange={(e) => setNuevo({ ...nuevo, fechaObjetivo: e.target.value })} />
        </label>
        <div className="botones">
          <button className="btn btn-primario" type="submit" disabled={!nuevo.nombre.trim()}>Añadir hito</button>
        </div>
      </form>
    </div>
  )
}

function Diario({ d, aplicar, id }) {
  const [nota, setNota] = useState({ tipo: 'NOTA', contenido: '' })
  const abiertas = d.notas.filter((n) => n.abierta)
  return (
    <div style={{ display: 'grid', gap: 20 }}>
      <div className="rejilla-resumen">
        <section className="panel">
          <div className="panel-cabecera">
            <h2 className="subtitulo">Riesgos y bloqueos abiertos</h2>
            <p className={abiertas.length ? 'vencido' : 'texto-3'}>{abiertas.length}</p>
          </div>
          <div className="panel-cuerpo">
            {abiertas.length === 0 ? <p className="texto-2">Ninguno abierto.</p> : (
              <ul className="diario">
                {abiertas.map((n) => (
                  <li key={n.id}>
                    <span className="tipo-nota" data-tipo={n.tipo}>{TIPOS_NOTA[n.tipo]}</span>
                    <span>{n.contenido}</span>
                    <button className="enlace pequeno" onClick={() => aplicar(api.actualizarNota(n.id, { abierta: false }))}>Resolver</button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <form className="panel formulario" style={{ marginBottom: 0 }} onSubmit={(e) => {
          e.preventDefault()
          aplicar(api.crearNota(id, nota))
          setNota({ tipo: 'NOTA', contenido: '' })
        }}>
          <h2 className="subtitulo">Anotar en el diario</h2>
          <label className="etiqueta-campo">Tipo
            <select className="campo" value={nota.tipo} onChange={(e) => setNota({ ...nota, tipo: e.target.value })}>
              {['DECISION', 'RIESGO', 'BLOQUEO', 'NOTA'].map((t) => <option key={t} value={t}>{TIPOS_NOTA[t]}</option>)}
            </select>
          </label>
          <label className="etiqueta-campo">Contenido
            <textarea className="campo" rows={3} required value={nota.contenido} onChange={(e) => setNota({ ...nota, contenido: e.target.value })} />
          </label>
          <div className="botones">
            <button className="btn btn-primario" type="submit" disabled={!nota.contenido.trim()}>Guardar</button>
          </div>
        </form>
      </div>

      <section className="panel">
        <div className="panel-cabecera"><h2 className="subtitulo">Historial del proyecto</h2></div>
        <div className="panel-cuerpo">
          <ul className="diario">
            {d.notas.map((n) => (
              <li key={n.id} className={!n.abierta && n.tipo !== 'CAMBIO' && n.tipo !== 'DECISION' && n.tipo !== 'NOTA' ? 'cerrada' : ''}>
                <span className="tipo-nota" data-tipo={n.tipo}>{TIPOS_NOTA[n.tipo]}</span>
                <span>{n.contenido}{!n.abierta && (n.tipo === 'RIESGO' || n.tipo === 'BLOQUEO') && <span className="texto-3">, resuelto</span>}</span>
                <span className="texto-3 pequeno" style={{ whiteSpace: 'nowrap' }}>{n.autor ? n.autor.nombre : 'Sistema'}, {fechaCorta(n.fecha)}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  )
}
