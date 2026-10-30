import { useEffect, useState } from 'react'
import { BookUser, FolderKanban, ListOrdered, LogOut, MessageSquareText, Network } from 'lucide-react'
import { api, usarEmpleado } from './api.js'
import { Marca, Punto } from './components/Comunes.jsx'
import Login from './components/Login.jsx'
import MisTareas from './components/MisTareas.jsx'
import Chat from './components/Chat.jsx'
import Grafo from './components/Grafo.jsx'
import Proyectos from './components/Proyectos.jsx'
import Directorio from './components/Directorio.jsx'

const VISTAS = [
  { id: 'tareas', etiqueta: 'Mis tareas', icono: ListOrdered },
  { id: 'chat', etiqueta: 'Asistente', icono: MessageSquareText },
  { id: 'proyectos', etiqueta: 'Proyectos', icono: FolderKanban },
  { id: 'grafo', etiqueta: 'Grafo de dependencias', icono: Network },
  { id: 'directorio', etiqueta: 'Quién hace qué', icono: BookUser },
]

function leerGuardado(clave) {
  try {
    return JSON.parse(localStorage.getItem(clave))
  } catch {
    return null
  }
}

function guardar(clave, valor) {
  try {
    if (valor === null) localStorage.removeItem(clave)
    else localStorage.setItem(clave, JSON.stringify(valor))
  } catch {
    // localStorage no disponible: la sesión simplemente no se recuerda.
  }
}

export default function App() {
  const [usuario, setUsuario] = useState(() => leerGuardado('orgbrain.usuario'))
  const [vista, setVista] = useState(() => leerGuardado('orgbrain.pestana') || 'tareas')
  const [estadoIa, setEstadoIa] = useState(null)
  // Se incrementa cuando el chat o los proyectos cambian datos, para que las demás vistas se recarguen.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    api.estadoIa().then(setEstadoIa).catch(() => setEstadoIa(null))
  }, [])

  // El backend registra como autor de cada cambio al usuario del login simulado.
  useEffect(() => {
    usarEmpleado(usuario?.id)
  }, [usuario])

  const entrar = (empleado) => {
    setUsuario(empleado)
    guardar('orgbrain.usuario', empleado)
  }
  const salir = () => {
    setUsuario(null)
    guardar('orgbrain.usuario', null)
  }
  const cambiarVista = (id) => {
    setVista(id)
    guardar('orgbrain.pestana', id)
  }
  const cambios = () => setVersion((v) => v + 1)

  if (!usuario) {
    return <Login onEntrar={entrar} />
  }

  return (
    <div className="app">
      <aside className="rail">
        <Marca />
        <nav className="rail-nav" aria-label="Vistas">
          {VISTAS.map(({ id, etiqueta, icono: Icono }) => (
            <button key={id} className="rail-item" aria-current={vista === id ? 'page' : undefined}
              onClick={() => cambiarVista(id)} title={etiqueta}>
              <Icono size={16} strokeWidth={1.5} aria-hidden="true" />
              <span>{etiqueta}</span>
            </button>
          ))}
        </nav>
        <div className="rail-pie">
          <div className="rail-usuario">
            <strong>{usuario.nombre}</strong>
            <small className="texto-2">{usuario.rol}</small>
          </div>
          {estadoIa && (
            <span className="estado-ia" title={estadoIa.geminiConfigurado
              ? `Asistente con ${estadoIa.modelo}`
              : 'Sin GEMINI_API_KEY: el asistente funciona con reglas simples'}>
              <Punto senal={estadoIa.geminiConfigurado ? 'verde' : 'ocre'} />
              {estadoIa.geminiConfigurado ? `Gemini activo` : 'Asistente sin IA'}
            </span>
          )}
          <button className="btn btn-sutil btn-pequeno" onClick={salir} style={{ justifyContent: 'flex-start', paddingLeft: 0 }}>
            <LogOut size={14} strokeWidth={1.5} aria-hidden="true" /> Cambiar de usuario
          </button>
        </div>
      </aside>

      <main className="principal">
        {vista === 'tareas' && <MisTareas usuario={usuario} version={version} />}
        {vista === 'chat' && <Chat usuario={usuario} onCambios={cambios} />}
        {vista === 'proyectos' && <Proyectos usuario={usuario} version={version} onCambios={cambios} />}
        {vista === 'grafo' && <Grafo usuario={usuario} version={version} />}
        {vista === 'directorio' && <Directorio />}
      </main>
    </div>
  )
}
