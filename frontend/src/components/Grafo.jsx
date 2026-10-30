import { useCallback, useEffect, useMemo, useState } from 'react'
import { applyNodeChanges, Background, Controls, Handle, MarkerType, MiniMap, Position, ReactFlow } from '@xyflow/react'
import { graphlib, layout } from '@dagrejs/dagre'
import { api } from '../api.js'
import { DEPARTAMENTOS, formatoUrgencia, senalUrgencia } from '../util.js'
import { Cargando, Estado, Motivo } from './Comunes.jsx'

const ANCHO_NODO = 240
const ALTO_NODO = 64
const SEPARACION = 60

/** Agrupa los nodos en componentes conexas (union-find). */
function componentes(grafo) {
  const padre = new Map(grafo.nodos.map((n) => [n.id, n.id]))
  const raiz = (x) => (padre.get(x) === x ? x : (padre.set(x, raiz(padre.get(x))), padre.get(x)))
  grafo.aristas.forEach((a) => padre.set(raiz(a.origen), raiz(a.destino)))
  const grupos = new Map()
  grafo.nodos.forEach((n) => {
    const r = raiz(n.id)
    if (!grupos.has(r)) grupos.set(r, [])
    grupos.get(r).push(n.id)
  })
  return [...grupos.values()]
}

/**
 * Cada componente se dispone con dagre de izquierda (bloqueantes) a derecha (bloqueadas) y luego las componentes
 * se empaquetan en filas, de mayor a menor, para que el grafo completo no sea una columna interminable.
 */
function disponer(grafo) {
  const aristasPorNodo = new Map()
  grafo.aristas.forEach((a) => {
    if (!aristasPorNodo.has(a.origen)) aristasPorNodo.set(a.origen, [])
    aristasPorNodo.get(a.origen).push(a)
  })

  const bloques = componentes(grafo).map((ids) => {
    const g = new graphlib.Graph()
    g.setGraph({ rankdir: 'LR', nodesep: 22, ranksep: 64 })
    g.setDefaultEdgeLabel(() => ({}))
    ids.forEach((id) => g.setNode(String(id), { width: ANCHO_NODO, height: ALTO_NODO }))
    ids.forEach((id) => (aristasPorNodo.get(id) || []).forEach((a) => g.setEdge(String(a.origen), String(a.destino))))
    layout(g)
    const posiciones = ids.map((id) => {
      const p = g.node(String(id))
      return { id: String(id), x: p.x - ANCHO_NODO / 2, y: p.y - ALTO_NODO / 2 }
    })
    const ancho = Math.max(...posiciones.map((p) => p.x)) + ANCHO_NODO
    const alto = Math.max(...posiciones.map((p) => p.y)) + ALTO_NODO
    return { posiciones, ancho, alto, tamano: ids.length }
  })
  bloques.sort((a, b) => b.tamano - a.tamano || b.ancho - a.ancho)

  const area = bloques.reduce((s, b) => s + (b.ancho + SEPARACION) * (b.alto + SEPARACION), 0)
  const anchoFila = Math.max(bloques[0]?.ancho ?? 0, Math.sqrt(area) * 1.8)
  const resultado = []
  let x = 0
  let y = 0
  let altoFila = 0
  for (const b of bloques) {
    if (x > 0 && x + b.ancho > anchoFila) {
      x = 0
      y += altoFila + SEPARACION
      altoFila = 0
    }
    b.posiciones.forEach((p) => resultado.push({ id: p.id, x: p.x + x, y: p.y + y }))
    x += b.ancho + SEPARACION
    altoFila = Math.max(altoFila, b.alto)
  }
  return resultado
}

/** Cadena completa de una tarea: todo lo que la bloquea (aguas arriba) y todo lo que bloquea (aguas abajo). */
function cadenaDe(id, aristas) {
  if (id == null) return null
  const recorrer = (desde, siguiente) => {
    const vistos = new Set([desde])
    const pila = [desde]
    while (pila.length) {
      const actual = pila.pop()
      for (const a of aristas) {
        const [de, a2] = siguiente(a)
        if (de === actual && !vistos.has(a2)) {
          vistos.add(a2)
          pila.push(a2)
        }
      }
    }
    return vistos
  }
  const arriba = recorrer(id, (a) => [a.destino, a.origen])
  const abajo = recorrer(id, (a) => [a.origen, a.destino])
  return { arriba, abajo, todos: new Set([...arriba, ...abajo]) }
}

function Ficha({ data }) {
  const t = data.tarea
  const clases = ['ficha']
  if (data.mia) clases.push('mia')
  if (t.estado === 'HECHA') clases.push('hecha')
  if (data.seleccionada) clases.push('seleccionada')
  else if (data.enCadena) clases.push('en-cadena')
  if (data.atenuada) clases.push('atenuada')
  return (
    <div className={clases.join(' ')} data-senal={senalUrgencia(t.urgenciaCalculada)} style={{ width: ANCHO_NODO }}>
      <Handle type="target" position={Position.Left} />
      <div className="ficha-cabecera">
        <span className="cifra color-senal" style={t.urgenciaCalculada < 3 ? { color: 'var(--texto-2)' } : undefined}>
          {formatoUrgencia(t.urgenciaCalculada)}
        </span>
        <span className="ficha-titulo" title={t.titulo}>{t.titulo}</span>
      </div>
      <div className="ficha-pie">
        <span>{data.mia ? 'Tú' : t.responsable.nombre}</span>
        <span>{t.proyecto ? t.proyecto.nombre : DEPARTAMENTOS[t.responsable.departamento]}</span>
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

const tiposNodo = { tarea: Ficha }

export default function Grafo({ usuario, version, proyectoId }) {
  const embebido = proyectoId != null
  const [alcance, setAlcance] = useState(embebido ? 'proyecto' : 'mio')
  const [grafo, setGrafo] = useState(null)
  const [error, setError] = useState(null)
  const [seleccion, setSeleccion] = useState(null)
  const [nodos, setNodos] = useState([])

  useEffect(() => {
    setGrafo(null)
    setSeleccion(null)
    const filtro = alcance === 'proyecto' ? { proyectoId } : alcance === 'mio' ? { empleadoId: usuario.id } : {}
    api.grafo(filtro).then(setGrafo).catch((e) => setError(e.message))
  }, [alcance, usuario.id, proyectoId, version])

  // El layout solo se recalcula cuando cambian los datos; la selección solo cambia el estilo.
  useEffect(() => {
    if (!grafo) return setNodos([])
    const posiciones = new Map(disponer(grafo).map((p) => [p.id, p]))
    setNodos(grafo.nodos.map((n) => ({
      id: String(n.id),
      type: 'tarea',
      position: { x: posiciones.get(String(n.id)).x, y: posiciones.get(String(n.id)).y },
      data: { tarea: n, mia: n.responsable.id === usuario.id },
    })))
  }, [grafo, usuario.id])

  const cadena = useMemo(() => (grafo ? cadenaDe(seleccion, grafo.aristas) : null), [grafo, seleccion])

  useEffect(() => {
    setNodos((ns) => ns.map((n) => {
      const id = Number(n.id)
      return {
        ...n,
        data: {
          ...n.data,
          seleccionada: id === seleccion,
          enCadena: cadena?.todos.has(id) ?? false,
          atenuada: cadena != null && !cadena.todos.has(id),
        },
      }
    }))
  }, [seleccion, cadena])

  const onNodesChange = useCallback((cambios) => setNodos((ns) => applyNodeChanges(cambios, ns)), [])

  const aristas = useMemo(() => {
    if (!grafo) return []
    const porId = new Map(grafo.nodos.map((n) => [n.id, n]))
    return grafo.aristas.map((a) => {
      const inactiva = porId.get(a.origen)?.estado === 'HECHA' || porId.get(a.destino)?.estado === 'HECHA'
      const tipo = inactiva ? 'arista-inactiva' : a.tipo === 'BLANDA' ? 'arista-blanda' : 'arista-dura'
      const enCadena = cadena && (
        (cadena.arriba.has(a.origen) && cadena.arriba.has(a.destino)) ||
        (cadena.abajo.has(a.origen) && cadena.abajo.has(a.destino)))
      return {
        id: `e${a.id}`,
        source: String(a.origen),
        target: String(a.destino),
        className: `${tipo} ${cadena ? (enCadena ? 'en-cadena' : 'atenuada') : ''}`,
        markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: enCadena ? 'var(--petroleo)' : undefined },
      }
    })
  }, [grafo, cadena])

  const seleccionada = grafo?.nodos.find((n) => n.id === seleccion)

  return (
    <div className={embebido ? '' : 'vista'}>
      <header className="vista-cabecera">
        <div>
          {!embebido && <h1 className="titulo-vista">Grafo de dependencias</h1>}
          <p className="lead">
            {embebido ? 'Tareas del proyecto y las de fuera conectadas con ellas. ' : ''}
            Cada flecha va de la tarea que bloquea a la que espera. Selecciona una tarea para ver su cadena completa.
          </p>
          <div className="leyenda-grafo" aria-label="Leyenda">
            <span><i className="muestra-linea" /> Dependencia dura</span>
            <span><i className="muestra-linea blanda" /> Blanda</span>
            <span><i className="muestra-linea inactiva" /> Ya no bloquea</span>
            <span>El borde izquierdo de cada tarea marca su urgencia calculada.</span>
          </div>
        </div>
        <div className="segmentado" role="group" aria-label="Alcance del grafo">
          {embebido
            ? <button aria-pressed={alcance === 'proyecto'} onClick={() => setAlcance('proyecto')}>Solo el proyecto</button>
            : <button aria-pressed={alcance === 'mio'} onClick={() => setAlcance('mio')}>Mi red</button>}
          <button aria-pressed={alcance === 'todo'} onClick={() => setAlcance('todo')}>Toda la empresa</button>
        </div>
      </header>

      {error && <div className="aviso aviso-error" role="alert">{error}</div>}

      <div className="grafo">
        <div className="lienzo">
          {!grafo && !error && <div style={{ padding: 16 }}><Cargando texto="Calculando el grafo…" /></div>}
          {grafo && grafo.nodos.length === 0 && (
            <div style={{ padding: 24 }}>
              <p className="texto-2">
                {embebido ? 'Ninguna tarea del proyecto tiene dependencias todavía. ' : 'Ninguna de tus tareas tiene dependencias todavía. '}
                <button className="enlace" onClick={() => setAlcance('todo')}>Ver el grafo de toda la empresa</button>
              </p>
            </div>
          )}
          {grafo && grafo.nodos.length > 0 && nodos.length === grafo.nodos.length && (
            // key: al cambiar de alcance se vuelve a montar para que fitView encuadre el grafo nuevo
            <ReactFlow
              key={alcance}
              nodes={nodos}
              edges={aristas}
              onNodesChange={onNodesChange}
              nodeTypes={tiposNodo}
              onNodeClick={(_, n) => setSeleccion(Number(n.id))}
              onPaneClick={() => setSeleccion(null)}
              nodesConnectable={false}
              fitView
              fitViewOptions={{ padding: 0.12 }}
              minZoom={0.1}
              proOptions={{ hideAttribution: true }}
            >
              <Background gap={24} size={1} />
              <MiniMap pannable zoomable nodeClassName={(n) => `minimapa-${senalUrgencia(n.data.tarea.urgenciaCalculada)}`} />
              <Controls showInteractive={false} />
            </ReactFlow>
          )}
        </div>

        <aside className="panel detalle-lateral" aria-live="polite">
          {!seleccionada ? (
            <>
              <div className="panel-cabecera"><h2 className="subtitulo">Detalle</h2></div>
              <div className="panel-cuerpo">
                <p className="texto-2">Selecciona una tarea para ver por qué tiene esa prioridad y su cadena de bloqueos.</p>
                {grafo && (
                  <table className="tabla">
                    <tbody>
                      <tr><td>Tareas</td><td className="num cifra">{grafo.nodos.length}</td></tr>
                      <tr><td>Dependencias</td><td className="num cifra">{grafo.aristas.length}</td></tr>
                      <tr>
                        <td>Con urgencia heredada</td>
                        <td className="num cifra">{grafo.nodos.filter((n) => n.estado !== 'HECHA' && n.urgenciaCalculada > n.urgenciaPropia + 0.001).length}</td>
                      </tr>
                    </tbody>
                  </table>
                )}
              </div>
            </>
          ) : (
            <>
              <div className="panel-cabecera">
                <h2 className="subtitulo">{seleccionada.titulo} <span className="id">#{seleccionada.id}</span></h2>
              </div>
              <div className="panel-cuerpo">
                <div className="meta" style={{ marginTop: 0 }}>
                  <span>{seleccionada.responsable.nombre}</span>
                  <span>{seleccionada.proyecto?.nombre ?? DEPARTAMENTOS[seleccionada.responsable.departamento]}</span>
                  <Estado estado={seleccionada.estado} />
                </div>
                <p>
                  Urgencia propia <span className="cifra">{seleccionada.urgenciaPropia}</span>, calculada{' '}
                  <span className="cifra color-senal" data-senal={senalUrgencia(seleccionada.urgenciaCalculada)}>
                    {formatoUrgencia(seleccionada.urgenciaCalculada)}
                  </span>.
                </p>
                {seleccionada.estado !== 'HECHA' && <Motivo tarea={seleccionada} />}
                {cadena && (
                  <p className="texto-2 pequeno">
                    En su cadena: {cadena.arriba.size - 1} tareas antes y {cadena.abajo.size - 1} después.
                  </p>
                )}
                <Vecinos grafo={grafo} id={seleccionada.id} onIr={setSeleccion} />
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  )
}

function Vecinos({ grafo, id, onIr }) {
  const porId = new Map(grafo.nodos.map((n) => [n.id, n]))
  const bloquea = grafo.aristas.filter((a) => a.origen === id).map((a) => ({ ...porId.get(a.destino), tipo: a.tipo }))
  const espera = grafo.aristas.filter((a) => a.destino === id).map((a) => ({ ...porId.get(a.origen), tipo: a.tipo }))
  const lista = (titulo, tareas) => (
    <div>
      <h3 className="subtitulo" style={{ marginBottom: 4 }}>{titulo}</h3>
      {tareas.length === 0 ? <p className="texto-3 pequeno">Ninguna.</p> : (
        <ul className="lista-deps">
          {tareas.map((t) => (
            <li key={t.id}>
              <span className="cifra color-senal" data-senal={senalUrgencia(t.urgenciaCalculada)}>{formatoUrgencia(t.urgenciaCalculada)}</span>
              <span>
                <button className="enlace" onClick={() => onIr(t.id)}>{t.titulo}</button>
                <span className="texto-2">, {t.responsable.nombre}{t.tipo === 'BLANDA' ? ' (blanda)' : ''}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
  return (
    <>
      {lista('Espera a', espera)}
      {lista('Bloquea a', bloquea)}
    </>
  )
}
