# OrgBrain — Cerebro central de empresa

Herramienta interna que prioriza el trabajo de una empresa según las **dependencias entre personas**: si tu tarea
bloquea a otras, su prioridad sube sola, sin que nadie tenga que reclamarla. Una petición de urgencia 1 que frena a
cuatro personas acaba por encima de un "urgente" que no bloquea a nadie.

**Qué resuelve.** En cualquier empresa, lo que de verdad frena un proyecto suele ser una gestión pequeña en el backlog
de otro departamento: una licencia, un acceso, un alta. Quien la tiene asignada no sabe a quién está bloqueando, y
quien espera solo puede insistir por correo. OrgBrain hace visible esa cadena y recoloca las prioridades solo.

Cuatro piezas:

| | |
|---|---|
| **Motor de prioridades** | Propaga la urgencia por el grafo de dependencias y escribe el motivo de cada subida |
| **Asistente por persona** | Crea tareas desde lenguaje natural, las asigna a quien corresponde, recuerda el contexto de cada usuario y aplica cambios hablando |
| **Gestión de proyectos** | Salud, avance, equipo, hitos, riesgos y decisiones, con mapa mental y grafo propios |
| **Grafo de dependencias** | Toda la red de bloqueos, con la cadena completa de una tarea al seleccionarla |

```
frontend/  React 19 + Vite + React Flow (login simulado, mis tareas, asistente, proyectos, grafo, directorio)
backend/   Spring Boot 4.1 (Java 21) + JPA + MySQL/MariaDB de XAMPP + Gemini API
```

> **Estado: prototipo (MVP v1).** Funciona de principio a fin con **datos simulados** (100 empleados, 300 tareas,
> 72 dependencias, 8 proyectos) generados al arrancar. No hay autenticación real (se elige usuario de una lista) ni
> integraciones con correo, Teams, Slack o Jira. Nada de lo que aparece en la app corresponde a personas reales.

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 o superior | Probado con JDK 26 |
| Node.js | 20 o superior | Probado con Node 24 |
| XAMPP | con MySQL | XAMPP trae **MariaDB 10.4**, compatible |
| Maven | — | No hace falta: se usa el wrapper `mvnw` incluido |
| API key de Gemini | opcional | Gratuita en https://aistudio.google.com/apikey |

## Puesta en marcha

### 1. Base de datos (XAMPP)

1. Abre el panel de control de XAMPP y pulsa **Start** en MySQL.
2. No hace falta crear nada: al arrancar, el backend crea la base de datos `orgbrain`, las tablas (`schema.sql`) y
   los datos simulados si está vacía.

Si tu MySQL no usa `root` sin contraseña, configura las variables `DB_USER` y `DB_PASSWORD` (ver tabla abajo).
Si prefieres crear el esquema a mano, importa `backend/src/main/resources/schema.sql` desde phpMyAdmin
(`http://localhost/phpmyadmin`) en una base de datos llamada `orgbrain`.

### 2. Backend

1. Copia `.env.example` como `.env` (en la raíz del proyecto) y pon tu clave: `GEMINI_API_KEY=...`.
   Es opcional: sin clave el chat funciona con reglas simples. El fichero `.env` no se sube al repositorio.
2. Arranca:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

También puedes pasar la clave como variable de entorno (`$env:GEMINI_API_KEY = "..."` en PowerShell), que tiene
prioridad sobre el `.env`, pero solo dura mientras esa ventana de terminal esté abierta.

La primera vez tarda un poco (descarga Maven y dependencias). Cuando veas en el log
`Seeder: 100 empleados, 17 dominios, 300 tareas, 72 dependencias`, la API está lista en `http://localhost:8080/api`.

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Abre `http://localhost:5173`. Vite redirige `/api` al backend (puerto 8080), así que no hay que configurar CORS.

### Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `GEMINI_API_KEY` | *(vacío)* | API key de Google AI Studio. Sin ella, el chat usa un extractor por reglas |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Modelo de Gemini (cualquiera con capa gratuita y salida estructurada) |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | Servidor MySQL/MariaDB |
| `DB_NAME` | `orgbrain` | Se crea sola si no existe |
| `DB_USER` / `DB_PASSWORD` | `root` / *(vacío)* | Credenciales por defecto de XAMPP |
| `SEED_ENABLED` | `true` | Generar datos simulados si la BD está vacía |
| `SERVER_PORT` | `8080` | Puerto del backend |
| `CORS_ORIGINS` | `http://localhost:5173` | Orígenes permitidos si el frontend no usa el proxy de Vite |
| `VITE_BACKEND_URL` | `http://localhost:8080` | (frontend) A dónde redirige Vite las llamadas a `/api` |

### Regenerar los datos de demo

El seeder es idempotente: si ya hay empleados no hace nada. Para empezar de cero, borra la base de datos
(en phpMyAdmin, o con `C:\xampp\mysql\bin\mysql.exe -u root -e "DROP DATABASE orgbrain;"`) y reinicia el backend.
La semilla aleatoria es fija, así que siempre se generan los mismos datos.

## Gestión de proyectos

Cada proyecto tiene responsable, estado (planificación, en curso, en riesgo, pausado, completado, cancelado),
prioridad 1-5, fechas, equipo con rol y dedicación, hitos y un diario de decisiones, riesgos, bloqueos y cambios.
Las tareas pertenecen a un proyecto y **la prioridad del proyecto empuja a sus tareas** en el motor
(prioridad 5: +0.5; prioridad 4: +0.25; en riesgo: +0.25).

La pestaña **Proyectos** muestra de un vistazo la salud de cada uno y, al abrirlo:

| Vista | Qué da |
|---|---|
| Resumen | Siguientes pasos (lo que se puede hacer ya), lo más prioritario, cuellos de botella y dependencias con otros proyectos |
| Mapa mental | Equipo, siguientes pasos, cuellos de botella, hitos, riesgos y dependencias externas alrededor del proyecto |
| Tareas | Tablero por estado y alta de tareas nuevas dentro del proyecto |
| Equipo | Quién trabaja en él, con rol, dedicación, tareas abiertas, cuántas esperan a otras y su carga total |
| Hitos | Línea de tiempo con los vencidos marcados |
| Diario | Riesgos y bloqueos abiertos, decisiones y el histórico de cambios con su autor |
| Dependencias | El grafo, limitado al proyecto y a las tareas de fuera conectadas con él |

**Salud del proyecto**: rojo si está marcado en riesgo, se pasó la fecha objetivo, hay hitos vencidos, hay 3 o más
tareas vencidas o el 50% o más de lo abierto espera a otras tareas; amarillo con alguna tarea vencida, riesgos
abiertos, 25% o más esperando, fecha objetivo a menos de 14 días con menos del 70% hecho, o proyecto pausado.

Todos los cambios (por web o por chat) quedan registrados en el diario del proyecto con quién los hizo. El login es
simulado, así que cualquiera puede editar cualquier proyecto: no hay permisos en esta v1.

### Desde el chat

- "¿Cómo va Orión?", "¿qué falta en Atlas?", "¿cómo van mis proyectos?" → salud, avance, prioridades, siguientes
  pasos, cuellos de botella, próximo hito y riesgos.
- "Pon Atlas en riesgo", "sube la prioridad de Nexo a 5", "la fecha objetivo de Fénix pasa al 30 de octubre".
- "Añade a Hugo Benítez al proyecto Orión como desarrollador backend al 40%", "quita a X del equipo",
  "el responsable de Lince pasa a ser Y".
- "Crea el proyecto Expansión a Portugal", "nuevo hito en Orión: ensayo general el viernes", "hito X completado".
- "Riesgo en Atlas: el proveedor no llega", "hemos decidido…", "ese riesgo ya está resuelto".
- "Marca la #303 como hecha", "sube la urgencia de la #12 a 5", "pásale la #45 a Laura", "mete la #77 en Orión".

## Guion de demo (5 minutos)

1. **Entra como Laura Martín Sánchez** (Administradora de Microsoft 365). Su primera tarea, *"Asignar licencias de
   GitHub Copilot al equipo de Orión"*, tiene urgencia propia **2** pero calculada **6.4**: 🔺 bloquea a 4 personas.
   Pulsa *Detalles* para ver a quién bloquea.
2. **Grafo de dependencias → Mi red**: se ve cómo esa tarea bloquea a tres desarrolladores y, en cadena, la demo de
   Orión de Ana. Cambia a *Toda la empresa* para ver las cadenas largas (migración al ERP Fénix, 6 eslabones).
3. **Cambia a Ana López García** (jefa del proyecto Orión) → *Asistente*. Escribe:
   *"Estoy desarrollando el proyecto Orión y me falta la licencia de Copilot para el equipo"*.
   El asistente crea una tarea en el backlog de Laura (responsable de licencias M365), la enlaza como subtarea que
   bloquea la demo de Orión de Ana y guarda la necesidad en memoria. El panel derecho muestra lo que recuerda de Ana
   (el seeder ya le dio contexto previo sobre Orión y Lince).
4. Repite el mismo mensaje: detecta que la petición ya existe y no la duplica.
5. Prueba *"Necesito acceso a la VPN para trabajar desde casa"* (va a IT redes) o
   *"Créame una subtarea para preparar las diapositivas de la demo dentro del proyecto Orión"* (tarea propia enlazada).
6. **Vuelve a Laura** y marca como *Hecha* la tarea de licencias: las prioridades se recalculan al momento y deja de
   bloquear a nadie.
7. **Proyectos**: abre *Atlas* (en rojo). En *Resumen* verás que lo que lo frena es el certificado SSL; en *Mapa
   mental*, el proyecto entero de un vistazo; en *Equipo*, quién va cargado. Prueba a decirle al asistente
   "¿cómo va Atlas?" y "añade a [alguien] al proyecto Atlas como analista".

Otros escenarios del seeder: el certificado SSL de **Javier Ortega** (urgencia 1) bloquea el lanzamiento del portal
Atlas en Ventas, Marketing y la PMO; el alta de un analista de **Marta Gil** (RRHH, urgencia 1) bloquea el cierre
trimestral de Finanzas; el presupuesto de traducción de **Carmen Ruiz** bloquea la internacionalización de Marketing.

## Cómo funciona

### Motor de repriorización

`MotorPriorizacionService` recorre el grafo de las hojas hacia arriba (orden topológico inverso, algoritmo de Kahn).
Para cada tarea abierta:

| Paso | Cálculo |
|---|---|
| Base | urgencia propia + bonus por fecha límite (vencida +1, ≤ 3 días +0.5, ≤ 7 días +0.25) + bonus del proyecto (prioridad 5 +0.5, prioridad 4 +0.25, en riesgo +0.25), máx. 5 |
| Heredada | máximo del *nivel* de las tareas que bloquea directamente (×1 si la dependencia es dura, ×0.6 si es blanda) |
| Nivel | max(base, heredada): es lo que se transmite hacia arriba, así la urgencia se propaga por cadenas largas |
| Bonus por bloqueo | +0.35 por cada tarea urgente (nivel ≥ 4) bloqueada directa o transitivamente, +0.1 por las demás, máx. 2.5 |
| Calculada | nivel + bonus, entre 1 y 10 |

Las tareas **hechas** no bloquean. También guarda el motivo legible (*"Hereda urgencia 5.0 de «…» (Hugo Benítez) ·
Bloquea a 4 tareas (4 urgentes) de 4 personas"*) y los contadores que muestra la interfaz. Se recalcula todo tras
cada cambio de tarea o dependencia (con 300 tareas tarda ~50 ms) y bajo demanda con `POST /api/priorizacion/recalcular`.
Al crear una dependencia se rechaza si formaría un ciclo.

### Chat, asignación y memoria

Cada mensaje (`ChatService`) sigue estos pasos:

1. **Contexto**: recupera la memoria relevante del usuario (coincidencia de palabras clave y proyecto, sin
   embeddings en v1), sus tareas abiertas, el directorio de responsabilidades y los últimos 10 mensajes.
2. **Extracción estructurada con Gemini** (`responseMimeType: application/json` + `responseSchema`):
   ```json
   {
     "peticiones":     [{ "tipo_peticion": "licencia|acceso|informacion|otro", "codigo_dominio": "LIC_M365",
                          "objeto": "licencia de GitHub Copilot", "proyecto_relacionado": "Orión",
                          "urgencia_estimada": 4, "es_subtarea_de": null }],
     "tareas_propias": [{ "titulo": "...", "urgencia_estimada": 3, "proyecto_relacionado": "...", "es_subtarea_de": "..." }],
     "memorias":       [{ "tipo": "proyecto_actual|necesidad_pendiente|decision|nota_general", "contenido": "...", "proyecto_relacionado": "..." }],
     "dependencias":   [{ "bloqueante": "#301", "bloqueada": "Automatizar el briefing semanal" }],
     "consultas_proyecto": ["Orión"],
     "cambios_proyecto":   [{ "accion": "cambiar_estado", "proyecto": "Atlas", "valor": "en riesgo",
                              "persona": null, "rol": null, "fecha": null, "tarea": null }],
     "cambios_tarea":      [{ "tarea": "#303", "estado": "hecha", "urgencia": null, "fecha_limite": null, "responsable": null }]
   }
   ```
   Es el esquema pedido, ampliado para admitir varias peticiones, tareas propias y memorias en un mismo mensaje, y
   `dependencias` para relaciones que el usuario expresa entre tareas existentes o nuevas ("sin la licencia no puedo
   hacer el briefing"). A Gemini se le pasan también las peticiones abiertas que el usuario ya ha hecho, con su `#id`.
3. **Asignación y enlaces** (`AsignacionService`), en este orden:
   1. Crea las peticiones (dominio propuesto por Gemini o, si no, por palabras clave; responsable con menos tareas
      abiertas) y las tareas propias. Si ya existían no se duplican: se reutilizan y sube su urgencia si ahora es mayor.
   2. Crea las `dependencias` explícitas, resueltas por `#id` o por título solo entre las tareas del usuario, sus
      peticiones abiertas y lo creado en ese mensaje. Se rechazan si crearían un ciclo.
   3. Enlaza con la tarea del proyecto (**dependencia dura**) solo lo que no bloquea ya a otra tarea del mensaje,
      para que la cadena quede `datos → guion → ensayo → demo` en vez de colgarlo todo de la demo. El proyecto casa
      con tareas `Proyecto: …` (que se crean si no existen) o, si es un proyecto con nombre propio conocido, con sus tareas.
   Los textos del JSON de Gemini se revisan para recuperar letras acentuadas que a veces llegan corruptas.
4. **Memoria**: guarda lo relevante evitando repeticiones.
5. **Respuesta en lenguaje natural** con Gemini, confirmando exactamente las acciones realizadas.

Las llamadas a Gemini se hacen fuera de las transacciones de base de datos. **Sin API key, o si Gemini falla**
(cuota agotada, red...), el chat sigue funcionando con `ExtractorHeuristico` (reglas para las frases típicas) y
una respuesta con plantilla. La interfaz indica en cada respuesta qué modo se ha usado.

## API REST

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/empleados?q=` | Empleados (login simulado) |
| GET | `/api/empleados/{id}/tareas?incluirHechas=` | Tareas del empleado ordenadas por urgencia calculada |
| GET | `/api/empleados/{id}/peticiones` | Tareas que ha pedido a otras personas |
| GET | `/api/empleados/{id}/memoria` | Memoria de contexto |
| GET, POST | `/api/tareas` | Listar / crear tarea (recalcula prioridades) |
| GET, PATCH | `/api/tareas/{id}` | Ver / modificar (estado, urgencia, título, fecha límite) |
| GET | `/api/tareas/{id}/dependencias` | Qué bloquea y quién la bloquea |
| GET, POST | `/api/dependencias` | Listar / crear (valida ciclos, recalcula) |
| DELETE | `/api/dependencias/{id}` | Eliminar (recalcula) |
| POST | `/api/priorizacion/recalcular` | Recálculo bajo demanda |
| GET | `/api/grafo?empleadoId=&proyectoId=&soloConDependencias=` | Nodos y aristas; se puede filtrar por persona o proyecto |
| GET | `/api/dominios` | Directorio de responsabilidades |
| GET | `/api/proyectos` | Proyectos con salud, avance y alertas |
| GET | `/api/proyectos/{id}` | Detalle: tareas, prioridades, siguientes pasos, cuellos, equipo, hitos y diario |
| POST, PATCH | `/api/proyectos`, `/api/proyectos/{id}` | Crear y modificar |
| PUT, DELETE | `/api/proyectos/{id}/miembros[/{empleadoId}]` | Equipo |
| POST, PATCH | `/api/proyectos/{id}/hitos`, `/api/hitos/{id}` | Hitos |
| POST, PATCH | `/api/proyectos/{id}/notas`, `/api/notas/{id}` | Diario y resolución de riesgos |
| POST | `/api/chat/{empleadoId}/mensajes` | Enviar mensaje al asistente `{ "mensaje": "..." }` |
| GET | `/api/chat/{empleadoId}/mensajes` | Historial |
| PATCH | `/api/memoria/{id}/archivar` | Marcar un recuerdo como no vigente |
| GET | `/api/ia/estado` | Si Gemini está configurado y qué modelo usa |

Los errores se devuelven como `ProblemDetail` (`{ "status", "title", "detail" }`). Las escrituras aceptan la cabecera
`X-Empleado-Id` (el usuario del login simulado), que se guarda como autor en el diario del proyecto.

## Estructura del backend

```
backend/src/main/java/com/orgbrain
├── config/       GeminiProperties, CORS
├── controller/   REST
├── dto/          records de entrada/salida + DtoMapper
├── entity/       entidades JPA y enums
├── exception/    errores → ProblemDetail
├── repository/   Spring Data JPA
├── seed/         datos simulados, proyectos y migraciones (CommandLineRunner, Datafaker)
├── service/      motor de priorización, chat, asignación, memoria, tareas, dependencias
│   └── ia/       GeminiClient, ExtractorHeuristico
└── util/         normalización de texto y palabras clave
backend/src/main/resources
├── application.yml
└── schema.sql    esquema compatible con MariaDB 10.4 / MySQL
```

Tests unitarios (motor, extractor por reglas, texto): `.\mvnw.cmd test`.
Tests de integración del chat con Gemini simulado (necesitan XAMPP; usan la BD `orgbrain_it`):
`.\mvnw.cmd test "-Dit=true"`.

## Notas técnicas

- **MariaDB 10.4 de XAMPP**: Hibernate 7 solo soporta oficialmente MariaDB ≥ 10.6 y genera `ALTER TABLE IF EXISTS`,
  que 10.4 no entiende. Por eso el esquema va en `schema.sql` (Hibernate solo lo valida) y se usa
  `MariaDBLegacyDialect`. El aviso *"version 10.4 … no longer supported"* al arrancar es esperado.
- **Proyectos conocidos** (`orgbrain.proyectos` en `application.yml`): los usa el seeder para generar títulos de
  tareas; el asistente reconoce los proyectos que hay en la base de datos.
- **Migración automática**: al arrancar sobre una base de datos anterior a la gestión de proyectos se añade la
  columna `tarea.proyecto_id`, se crean los 8 proyectos, se les asignan las tareas por su título, las tareas paraguas
  "Proyecto: …" que creaba el asistente pasan a ser proyectos reales y se reparan los textos que Gemini devolvió con
  letras corruptas. Solo ocurre si todavía no hay ningún proyecto.

## Fuera de alcance en v1 / siguientes pasos

- Integraciones con correo, Teams, Slack, Jira.
- Autenticación corporativa (SSO / Entra ID).
- Aprender el directorio "quién hace qué" observando patrones (en v1 es fijo, del seeder).
- Permisos: cualquiera puede modificar cualquier proyecto (el login es simulado), aunque todo queda firmado en el diario.
- Memoria con embeddings y búsqueda semántica (en v1, palabras clave).
