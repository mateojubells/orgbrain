import { useCallback, useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { api } from '../api.js'
import { ESTADOS_PROYECTO, SALUD, diasHasta, fechaCorta } from '../util.js'
import { Cargando, Punto } from './Comunes.jsx'
import ProyectoDetalle from './ProyectoDetalle.jsx'

const FILTROS = [
  ['activos', 'Activos'],
  ['mios', 'En los que estoy'],
  ['todos', 'Todos'],
]

export default function Proyectos({ usuario, version, onCambios }) {
  const [proyectos, setProyectos] = useState(null)
  const [seleccionado, setSeleccionado] = useState(null)
  const [filtro, setFiltro] = useState('activos')
  const [error, setError] = useState(null)
  const [creando, setCreando] = useState(false)

  const cargar = useCallback(() => api.proyectos().then(setProyectos).catch((e) => setError(e.message)), [])

  useEffect(() => {
    cargar()
  }, [cargar, version])

  if (seleccionado) {
    return (
      <ProyectoDetalle id={seleccionado} usuario={usuario}
        onVolver={() => { setSeleccionado(null); cargar() }} onCambios={onCambios} />
    )
  }

  const visibles = proyectos?.filter((p) => {
    if (filtro === 'mios') return p.participo
    if (filtro === 'activos') return !['COMPLETADO', 'CANCELADO'].includes(p.estado)
    return true
  })
  const enProblemas = proyectos?.filter((p) => p.salud.nivel === 'ROJO').length ?? 0

  return (
    <div className="vista">
      <header className="vista-cabecera">
        <div>
          <h1 className="titulo-vista">Proyectos</h1>
          <p className="lead">
            La salud se calcula con el plazo, los hitos, las tareas que esperan a otras y los riesgos abiertos.
            {proyectos && enProblemas > 0 && <> Ahora mismo {enProblemas === 1 ? 'hay 1 en problemas' : `hay ${enProblemas} en problemas`}.</>}
          </p>
        </div>
        <div className="vista-acciones">
          <div className="segmentado" role="group" aria-label="Filtro">
            {FILTROS.map(([v, t]) => (
              <button key={v} aria-pressed={filtro === v} onClick={() => setFiltro(v)}>{t}</button>
            ))}
          </div>
          <button className="btn btn-primario" onClick={() => setCreando(true)}>
            <Plus size={14} strokeWidth={2} aria-hidden="true" /> Nuevo proyecto
          </button>
        </div>
      </header>

      {error && <div className="aviso aviso-error" role="alert">{error}</div>}
      {creando && (
        <FormularioProyecto usuario={usuario} onCancelar={() => setCreando(false)} onError={setError}
          onCreado={(d) => { setCreando(false); cargar(); setSeleccionado(d.resumen.id) }} />
      )}

      {!proyectos && !error && <Cargando />}
      {visibles?.length === 0 && <p className="vacio">No hay proyectos con este filtro.</p>}

      {visibles?.length > 0 && (
        <div className="panel tabla-desplazable">
          <table className="tabla cartera">
            <thead>
              <tr>
                <th className="celda-salud"><span className="sr-only">Salud</span></th>
                <th>Proyecto</th>
                <th>Estado</th>
                <th className="num">Prioridad</th>
                <th>Responsable</th>
                <th>Plazo y avance</th>
                <th className="num">Abiertas</th>
                <th className="num">Esperan</th>
                <th className="num">Vencidas</th>
                <th>Próximo hito</th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((p) => (
                <tr key={p.id} className="fila-clicable" onClick={() => setSeleccionado(p.id)}>
                  <td className="celda-salud"><Punto senal={SALUD[p.salud.nivel].senal} titulo={SALUD[p.salud.nivel].texto} /></td>
                  <td>
                    <button className="enlace nombre" onClick={(e) => { e.stopPropagation(); setSeleccionado(p.id) }}>{p.nombre}</button>
                    <p className="motivo-salud">
                      <span className="sr-only">{SALUD[p.salud.nivel].texto}: </span>{p.salud.motivos[0]}
                    </p>
                  </td>
                  <td className={p.estado === 'EN_RIESGO' ? 'vencido' : 'texto-2'}>{ESTADOS_PROYECTO[p.estado]}</td>
                  <td className="num">{p.prioridad}</td>
                  <td>{p.responsable.nombre}{p.participo && <div className="texto-2 pequeno">Participas</div>}</td>
                  <td><Plazo proyecto={p} /></td>
                  <td className="num">{p.tareasAbiertas}</td>
                  <td className={`num ${p.tareasBloqueadas > 0 ? 'fuerte' : 'texto-3'}`}>{p.tareasBloqueadas}</td>
                  <td className={`num ${p.tareasVencidas > 0 ? 'vencido' : 'texto-3'}`}>{p.tareasVencidas}</td>
                  <td className="pequeno">
                    {p.proximoHito ? (
                      <>
                        {p.proximoHito.nombre}
                        <div className={p.proximoHito.vencido ? 'vencido' : 'texto-2'}>
                          {p.proximoHito.fechaObjetivo ? fechaCorta(p.proximoHito.fechaObjetivo) : 'Sin fecha'}
                          {p.proximoHito.vencido && ', vencido'}
                        </div>
                      </>
                    ) : <span className="texto-3">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/**
 * Plazo contra avance: la línea gris es el tiempo transcurrido entre inicio y fecha objetivo; la de petróleo, el
 * trabajo hecho. Si el avance va por detrás del tiempo, el hueco se marca en ocre (o carmín si ya venció).
 */
export function Plazo({ proyecto: p }) {
  const avance = Math.max(0, Math.min(100, p.progreso))
  const dias = diasHasta(p.fechaObjetivo)
  let tiempo = null
  if (p.fechaInicio && p.fechaObjetivo) {
    const total = (new Date(p.fechaObjetivo) - new Date(p.fechaInicio)) / 86400000
    const pasado = total - dias
    tiempo = total > 0 ? Math.max(0, Math.min(100, Math.round((pasado / total) * 100))) : 100
  }
  const retraso = tiempo != null && tiempo > avance
  const senal = dias != null && dias < 0 ? 'carmin' : 'ocre'
  const etiqueta = `${avance} % hecho` + (tiempo != null ? `, ${tiempo} % del plazo consumido` : '')

  return (
    <div className="plazo" data-senal={senal} role="img" aria-label={etiqueta}>
      <div className="plazo-barras">
        <div className="plazo-pista" />
        {tiempo != null && <div className="plazo-tiempo" style={{ width: `${tiempo}%` }} />}
        <div className="plazo-avance" style={{ width: `${avance}%` }} />
        {retraso && <div className="plazo-retraso" style={{ left: `${avance}%`, width: `${tiempo - avance}%` }} />}
      </div>
      <div className="plazo-texto">
        <span>{avance} %</span>
        {dias != null && (
          <span className={dias < 0 ? 'vencido' : ''}>{dias < 0 ? `vencido hace ${-dias} d` : `quedan ${dias} d`}</span>
        )}
      </div>
    </div>
  )
}

function FormularioProyecto({ usuario, onCancelar, onCreado, onError }) {
  const [empleados, setEmpleados] = useState([])
  const [datos, setDatos] = useState({
    nombre: '', objetivo: '', responsableId: usuario.id, prioridad: 3, estado: 'EN_CURSO', fechaObjetivo: '',
  })
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    api.empleados().then(setEmpleados).catch(() => {})
  }, [])

  const enviar = async (e) => {
    e.preventDefault()
    setGuardando(true)
    try {
      onCreado(await api.crearProyecto({
        ...datos,
        prioridad: Number(datos.prioridad),
        responsableId: Number(datos.responsableId),
        fechaObjetivo: datos.fechaObjetivo || null,
      }))
    } catch (err) {
      onError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form className="panel formulario" onSubmit={enviar}>
      <h2 className="titulo-seccion">Nuevo proyecto</h2>
      <div className="rejilla-campos">
        <label className="etiqueta-campo">Nombre
          <input className="campo" required value={datos.nombre} onChange={(e) => setDatos({ ...datos, nombre: e.target.value })} />
        </label>
        <label className="etiqueta-campo">Responsable
          <select className="campo" value={datos.responsableId} onChange={(e) => setDatos({ ...datos, responsableId: e.target.value })}>
            {empleados.map((e) => <option key={e.id} value={e.id}>{e.nombre}</option>)}
          </select>
        </label>
        <label className="etiqueta-campo">Prioridad
          <select className="campo" value={datos.prioridad} onChange={(e) => setDatos({ ...datos, prioridad: e.target.value })}>
            {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <label className="etiqueta-campo">Estado
          <select className="campo" value={datos.estado} onChange={(e) => setDatos({ ...datos, estado: e.target.value })}>
            {Object.entries(ESTADOS_PROYECTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
          </select>
        </label>
        <label className="etiqueta-campo">Fecha objetivo
          <input className="campo" type="date" value={datos.fechaObjetivo} onChange={(e) => setDatos({ ...datos, fechaObjetivo: e.target.value })} />
        </label>
        <label className="etiqueta-campo ancho-completo">Objetivo
          <input className="campo" placeholder="Qué se quiere conseguir, en una frase"
            value={datos.objetivo} onChange={(e) => setDatos({ ...datos, objetivo: e.target.value })} />
        </label>
      </div>
      <div className="botones">
        <button className="btn btn-primario" type="submit" disabled={guardando || !datos.nombre.trim()}>Crear proyecto</button>
        <button className="btn" type="button" onClick={onCancelar}>Cancelar</button>
      </div>
    </form>
  )
}
