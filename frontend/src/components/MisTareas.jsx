import { useCallback, useEffect, useState } from 'react'
import { ChevronDown, RefreshCw } from 'lucide-react'
import { api } from '../api.js'
import { DEPARTAMENTOS, ESTADOS, fechaCorta, plural, subio, vencida } from '../util.js'
import { Cargando, Estado, Motivo, Regla, Urgencia, useReordenAnimado } from './Comunes.jsx'

export default function MisTareas({ usuario, version }) {
  const [tareas, setTareas] = useState(null)
  const [peticiones, setPeticiones] = useState([])
  const [incluirHechas, setIncluirHechas] = useState(false)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [recalculando, setRecalculando] = useState(false)
  const [animar, setAnimar] = useState(false)
  const [abierta, setAbierta] = useState(null)

  const cargar = useCallback(() => {
    setError(null)
    return Promise.all([api.tareasDe(usuario.id, incluirHechas), api.peticionesDe(usuario.id)])
      .then(([t, p]) => {
        setTareas(t)
        setPeticiones(p)
      })
      .catch((e) => setError(e.message))
  }, [usuario.id, incluirHechas])

  useEffect(() => {
    setAnimar(false)
    cargar()
  }, [cargar, version])

  const orden = tareas?.map((t) => t.id).join(',')
  const lista = useReordenAnimado(orden, animar)

  const recalcular = async () => {
    setRecalculando(true)
    try {
      const r = await api.recalcular()
      setAnimar(true)
      await cargar()
      setAviso(`Prioridades recalculadas: ${plural(r.tareasRecalculadas, 'tarea')} en ${r.duracionMs} ms, `
        + `${r.tareasQueSubieron} con urgencia heredada.`)
    } catch (e) {
      setError(e.message)
    } finally {
      setRecalculando(false)
    }
  }

  const cambiarEstado = async (tarea, estado) => {
    try {
      await api.actualizarTarea(tarea.id, { estado })
      setAnimar(true)
      await cargar()
      setAviso(estado === 'HECHA' && tarea.tareasBloqueadas > 0
        ? `«${tarea.titulo}» está hecha: deja de bloquear y las prioridades se han recalculado.`
        : null)
    } catch (e) {
      setError(e.message)
    }
  }

  const subidas = tareas?.filter((t) => subio(t) && t.estado !== 'HECHA').length ?? 0

  return (
    <div className="vista">
      <header className="vista-cabecera">
        <div>
          <h1 className="titulo-vista">Mis tareas</h1>
          <p className="lead">
            Ordenadas por urgencia calculada: la tuya más la que heredas de quien espera por ti.
            {tareas && subidas > 0 && <> {plural(subidas, 'tarea ha subido', 'tareas han subido')} por dependencias.</>}
          </p>
        </div>
        <div className="vista-acciones">
          <label className="casilla">
            <input type="checkbox" checked={incluirHechas} onChange={(e) => setIncluirHechas(e.target.checked)} />
            Mostrar hechas
          </label>
          <button className="btn" onClick={recalcular} disabled={recalculando}>
            <RefreshCw size={14} strokeWidth={1.75} className={recalculando ? 'girando' : ''} aria-hidden="true" />
            Recalcular prioridades
          </button>
        </div>
      </header>

      {error && <div className="aviso aviso-error" role="alert">{error}</div>}
      {aviso && (
        <div className="aviso aviso-info" role="status">
          <span>{aviso}</span>
          <button className="btn btn-sutil btn-pequeno cerrar" onClick={() => setAviso(null)}>Cerrar</button>
        </div>
      )}

      {!tareas && !error && <Cargando />}
      {tareas?.length === 0 && <p className="vacio">No tienes tareas {incluirHechas ? '' : 'abiertas'}.</p>}

      {tareas?.length > 0 && (
        <div className="cola" role="table" aria-label="Cola de tareas por urgencia calculada">
          <div className="cola-cabecera leyenda" role="row">
            <span role="columnheader" className="cola-pos">Nº</span>
            <span role="columnheader">Urgencia</span>
            <span role="columnheader">Tarea</span>
            <span role="columnheader" className="col-proyecto">Proyecto</span>
            <span role="columnheader">Estado</span>
            <span role="columnheader">Límite</span>
            <span role="columnheader"><span className="sr-only">Detalle</span></span>
          </div>
          <ol ref={lista}>
            {tareas.map((t, i) => (
              <FilaTarea key={t.id} tarea={t} posicion={i + 1} abierta={abierta === t.id}
                onAlternar={() => setAbierta(abierta === t.id ? null : t.id)} onEstado={cambiarEstado} />
            ))}
          </ol>
        </div>
      )}

      {peticiones.length > 0 && (
        <section style={{ marginTop: 36 }}>
          <h2 className="titulo-seccion" style={{ marginBottom: 10 }}>Lo que he pedido a otras personas</h2>
          <div className="panel tabla-desplazable">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Petición</th>
                  <th>Responsable</th>
                  <th>Estado</th>
                  <th className="num">Urgencia</th>
                  <th>Pedida</th>
                </tr>
              </thead>
              <tbody>
                {peticiones.map((p) => (
                  <tr key={p.id}>
                    <td><span className="id">#{p.id}</span> {p.titulo.replace(/^Petición de [^:]+:\s*/, '')}</td>
                    <td>{p.responsable.nombre}<div className="texto-2 pequeno">{DEPARTAMENTOS[p.responsable.departamento]}</div></td>
                    <td><Estado estado={p.estado} /></td>
                    <td className="num"><Urgencia propia={p.urgenciaPropia} calculada={p.urgenciaCalculada} /></td>
                    <td className="texto-2">{fechaCorta(p.fechaCreacion)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}

function FilaTarea({ tarea: t, posicion, abierta, onAlternar, onEstado }) {
  const [deps, setDeps] = useState(null)

  useEffect(() => {
    if (abierta && !deps) {
      api.dependenciasDe(t.id).then(setDeps).catch(() => setDeps({ bloquea: [], bloqueadaPor: [] }))
    }
  }, [abierta, deps, t.id])

  const hecha = t.estado === 'HECHA'
  const esVencida = vencida(t)

  return (
    <li className={`cola-item ${abierta ? 'abierta' : ''}`} data-clave={t.id} role="row">
      <div className={`cola-fila ${subio(t) && !hecha ? 'subida' : ''} ${hecha ? 'hecha' : ''}`}>
        <span className="cola-pos" role="cell" aria-label={`Posición ${posicion}`}>{posicion}</span>
        <span className="cola-urgencia" role="cell">
          <Urgencia calculada={t.urgenciaCalculada} />
          <Regla propia={t.urgenciaPropia} calculada={t.urgenciaCalculada} />
        </span>
        <div className="cola-tarea" role="cell">
          <span className="titulo">{t.titulo}</span> <span className="id">#{t.id}</span>
          {!hecha && <Motivo tarea={t} />}
          {(t.solicitante || t.bloqueantesPendientes > 0) && (
            <p className="detalle-linea">
              {t.solicitante && <>Pedida por {t.solicitante.nombre}. </>}
              {t.bloqueantesPendientes > 0 && (
                <span className="bloqueo">Espera a {plural(t.bloqueantesPendientes, 'tarea')}.</span>
              )}
            </p>
          )}
        </div>
        <span className="cola-celda col-proyecto" role="cell" data-etiqueta="Proyecto">{t.proyecto?.nombre ?? '—'}</span>
        <span className="cola-celda" role="cell">
          <select className="campo campo-compacto" value={t.estado} aria-label={`Estado de la tarea ${t.id}`}
            onChange={(e) => onEstado(t, e.target.value)}>
            {Object.entries(ESTADOS).map(([v, texto]) => <option key={v} value={v}>{texto}</option>)}
          </select>
        </span>
        <span className={`cola-celda ${esVencida ? 'vencido' : ''}`} role="cell" data-etiqueta="Límite"
          title={esVencida ? 'Fecha límite vencida' : undefined}>
          {t.fechaLimite ? fechaCorta(t.fechaLimite) : '—'}
          {esVencida && (
            <>
              <svg width="8" height="8" viewBox="0 0 8 8" style={{ marginLeft: 5 }} aria-hidden="true"><path d="M4 0 L8 4 L4 8 L0 4 Z" fill="currentColor" /></svg>
              <span className="sr-only">, vencida</span>
            </>
          )}
        </span>
        <button className="cola-alternar" onClick={onAlternar} aria-expanded={abierta}
          aria-label={abierta ? 'Ocultar detalle' : 'Ver detalle'}>
          <ChevronDown size={16} strokeWidth={1.75} aria-hidden="true" />
        </button>
      </div>

      {abierta && (
        <div className="cola-detalle">
          {t.descripcion && <p className="descripcion">{t.descripcion}</p>}
          {!deps ? <Cargando texto="Cargando dependencias…" /> : (
            <div className="dos-columnas">
              <ListaDeps titulo="Bloquea a" vacia="No bloquea a ninguna tarea." tareas={deps.bloquea} />
              <ListaDeps titulo="Espera a" vacia="Nada la bloquea." tareas={deps.bloqueadaPor} />
            </div>
          )}
        </div>
      )}
    </li>
  )
}

function ListaDeps({ titulo, vacia, tareas }) {
  return (
    <div>
      <h3 className="subtitulo" style={{ marginBottom: 4 }}>{titulo}</h3>
      {tareas.length === 0 ? <p className="texto-2 pequeno">{vacia}</p> : (
        <ul className="lista-deps">
          {tareas.map((t) => (
            <li key={t.id}>
              <Urgencia calculada={t.urgenciaCalculada} />
              <span className={t.estado === 'HECHA' ? 'hecha' : ''}>
                <span className="id">#{t.id}</span> {t.titulo}
                <span className="texto-2">, {t.responsable.nombre}{t.tipoDependencia === 'BLANDA' ? ' (dependencia blanda)' : ''}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
