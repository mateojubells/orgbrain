import { useMemo } from 'react'
import { Background, Controls, Handle, Position, ReactFlow } from '@xyflow/react'
import { graphlib, layout } from '@dagrejs/dagre'
import { ESTADOS, SALUD, TIPOS_NOTA, fechaCorta, plural } from '../util.js'
import { Urgencia } from './Comunes.jsx'

const ANCHOS = { raiz: 230, rama: 200, hoja: 250 }
const ALTOS = { raiz: 90, rama: 46, hoja: 60 }

/** Ramas del mapa y sus hojas, a partir del detalle del proyecto. */
function ramas(d) {
  const r = d.resumen
  return [
    {
      id: 'equipo', titulo: 'Equipo',
      hojas: d.equipo.slice(0, 8).map((m) => ({
        id: `eq-${m.empleado.id}`,
        titulo: m.empleado.nombre,
        detalle: `${m.rolProyecto ?? m.rol}. ${plural(m.tareasAbiertas, 'tarea abierta', 'tareas abiertas')}`
          + (m.tareasEsperando > 0 ? `, ${m.tareasEsperando} esperando` : ''),
        hijos: d.tareas
          .filter((t) => t.responsable.id === m.empleado.id && t.estado !== 'HECHA')
          .slice(0, 3)
          .map((t) => ({ id: `eqt-${m.empleado.id}-${t.id}`, titulo: `#${t.id} ${t.titulo}`, detalle: ESTADOS[t.estado], urgencia: t.urgenciaCalculada })),
      })),
    },
    {
      id: 'siguientes', titulo: 'Siguientes pasos',
      hojas: d.siguientesPasos.map((t) => ({
        id: `sig-${t.id}`, titulo: `#${t.id} ${t.titulo}`, urgencia: t.urgenciaCalculada,
        detalle: `${t.responsable.nombre}, ${ESTADOS[t.estado].toLowerCase()}`,
      })),
    },
    {
      id: 'cuellos', titulo: 'Cuellos de botella',
      hojas: d.cuellosDeBotella.map((t) => ({
        id: `cue-${t.id}`, titulo: `#${t.id} ${t.titulo}`, urgencia: t.urgenciaCalculada,
        detalle: `${t.responsable.nombre}. Bloquea a ${plural(t.tareasBloqueadas, 'tarea')}`,
      })),
    },
    {
      id: 'hitos', titulo: 'Hitos',
      hojas: d.hitos.map((h) => ({
        id: `hito-${h.id}`, titulo: h.nombre,
        detalle: (h.fechaObjetivo ? fechaCorta(h.fechaObjetivo) : 'Sin fecha')
          + (h.completado ? ', completado' : h.vencido ? ', vencido' : ''),
        apagado: h.completado, alerta: h.vencido,
      })),
    },
    {
      id: 'riesgos', titulo: 'Riesgos y decisiones',
      hojas: d.notas.filter((n) => n.abierta || n.tipo === 'DECISION').slice(0, 6).map((n) => ({
        id: `nota-${n.id}`, titulo: `${TIPOS_NOTA[n.tipo]}: ${n.contenido}`,
        detalle: n.abierta ? 'Abierto' : 'Registrado', alerta: n.abierta,
      })),
    },
    {
      id: 'externas', titulo: 'Fuera del proyecto',
      hojas: d.dependenciasExternas.slice(0, 6).map((x, i) => ({
        id: `ext-${i}`,
        titulo: x.entrante
          ? `Nos bloquea #${x.bloqueante.id} ${x.bloqueante.titulo}`
          : `Bloqueamos #${x.bloqueada.id} ${x.bloqueada.titulo}`,
        detalle: x.entrante
          ? `${x.bloqueante.responsable.nombre}${x.proyectoBloqueante ? ` (${x.proyectoBloqueante.nombre})` : ''}`
          : `${x.bloqueada.responsable.nombre}${x.proyectoBloqueada ? ` (${x.proyectoBloqueada.nombre})` : ''}`,
        alerta: x.entrante,
      })),
    },
  ].filter((rama) => rama.hojas.length > 0).map((rama) => ({ ...rama, raiz: r }))
}

const SEPARACION_RAMAS = 36
const HUECO_COLUMNAS = 90

/**
 * Coloca el mapa: cada rama se dispone por su cuenta (dagre, de izquierda a derecha) y luego las ramas se
 * reparten en dos columnas equilibradas, con el proyecto a la izquierda y centrado. Así el mapa no se convierte
 * en una columna interminable cuando hay muchas hojas.
 */
function colocar(nodos, aristas) {
  const porId = new Map(nodos.map((n) => [n.id, n]))
  const hijos = new Map()
  aristas.forEach((a) => hijos.set(a.source, [...(hijos.get(a.source) ?? []), a.target]))

  const bloques = (hijos.get('raiz') ?? []).map((ramaId) => {
    const ids = [ramaId]
    for (let i = 0; i < ids.length; i++) {
      ;(hijos.get(ids[i]) ?? []).forEach((h) => ids.push(h))
    }
    const g = new graphlib.Graph()
    g.setGraph({ rankdir: 'LR', nodesep: 10, ranksep: 40 })
    g.setDefaultEdgeLabel(() => ({}))
    ids.forEach((id) => g.setNode(id, { width: ANCHOS[porId.get(id).data.tipo], height: ALTOS[porId.get(id).data.tipo] }))
    aristas.filter((a) => ids.includes(a.source) && ids.includes(a.target)).forEach((a) => g.setEdge(a.source, a.target))
    layout(g)
    const posiciones = ids.map((id) => {
      const p = g.node(id)
      const tipo = porId.get(id).data.tipo
      return { id, x: p.x - ANCHOS[tipo] / 2, y: p.y - ALTOS[tipo] / 2, ancho: ANCHOS[tipo], alto: ALTOS[tipo] }
    })
    return {
      posiciones,
      ancho: Math.max(...posiciones.map((p) => p.x + p.ancho)),
      alto: Math.max(...posiciones.map((p) => p.y + p.alto)),
    }
  })
  bloques.sort((a, b) => b.alto - a.alto)

  const anchoColumna = Math.max(0, ...bloques.map((b) => b.ancho))
  const columnas = [{ x: 0, alto: 0 }, { x: anchoColumna + HUECO_COLUMNAS, alto: 0 }]
  const posicionFinal = new Map()
  for (const b of bloques) {
    const col = columnas[0].alto <= columnas[1].alto ? columnas[0] : columnas[1]
    b.posiciones.forEach((p) => posicionFinal.set(p.id, { x: p.x + col.x, y: p.y + col.alto }))
    col.alto += b.alto + SEPARACION_RAMAS
  }
  const altoTotal = Math.max(columnas[0].alto, columnas[1].alto)
  posicionFinal.set('raiz', { x: -(ANCHOS.raiz + HUECO_COLUMNAS), y: altoTotal / 2 - ALTOS.raiz / 2 })

  return nodos.map((n) => ({ ...n, position: posicionFinal.get(n.id) ?? { x: 0, y: 0 } }))
}

function Nodo({ data }) {
  const { tipo, titulo, detalle, urgencia, alerta, apagado } = data
  const clases = ['nodo-mapa', tipo]
  if (alerta) clases.push('alerta')
  if (apagado) clases.push('apagado')
  return (
    <div className={clases.join(' ')} style={{ width: ANCHOS[tipo] }}>
      <Handle type="target" position={Position.Left} />
      <div className="titulo">
        {urgencia != null && <Urgencia calculada={urgencia} />}
        {titulo}
      </div>
      {detalle && <div className="detalle">{detalle}</div>}
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

const tiposNodo = { mapa: Nodo }

/** Mapa mental del proyecto: el proyecto en el centro-izquierda y sus ramas hacia la derecha. */
export default function MapaMental({ detalle }) {
  const { nodos, aristas } = useMemo(() => {
    const r = detalle.resumen
    const nodos = [{
      id: 'raiz', type: 'mapa', position: { x: 0, y: 0 },
      data: {
        tipo: 'raiz', titulo: r.nombre,
        detalle: `${SALUD[r.salud.nivel].texto}. ${r.progreso} % hecho, ${r.tareasAbiertas} abiertas.`,
      },
    }]
    const aristas = []
    for (const rama of ramas(detalle)) {
      nodos.push({
        id: rama.id, type: 'mapa', position: { x: 0, y: 0 },
        data: { tipo: 'rama', titulo: rama.titulo },
      })
      aristas.push({ id: `r-${rama.id}`, source: 'raiz', target: rama.id, className: 'arista-mapa' })
      for (const hoja of rama.hojas) {
        nodos.push({
          id: hoja.id, type: 'mapa', position: { x: 0, y: 0 },
          data: { tipo: 'hoja', titulo: hoja.titulo, detalle: hoja.detalle, urgencia: hoja.urgencia, alerta: hoja.alerta, apagado: hoja.apagado },
        })
        aristas.push({ id: `h-${hoja.id}`, source: rama.id, target: hoja.id, className: 'arista-mapa' })
        for (const hijo of hoja.hijos ?? []) {
          nodos.push({
            id: hijo.id, type: 'mapa', position: { x: 0, y: 0 },
            data: { tipo: 'hoja', titulo: hijo.titulo, detalle: hijo.detalle, urgencia: hijo.urgencia },
          })
          aristas.push({ id: `c-${hijo.id}`, source: hoja.id, target: hijo.id, className: 'arista-mapa' })
        }
      }
    }

    return { nodos: colocar(nodos, aristas), aristas }
  }, [detalle])

  return (
    <div className="lienzo mapa">
      <ReactFlow
        nodes={nodos}
        edges={aristas}
        nodeTypes={tiposNodo}
        nodesConnectable={false}
        fitView
        fitViewOptions={{ padding: 0.1 }}
        minZoom={0.1}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={24} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  )
}
