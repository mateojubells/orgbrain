# OrgBrain — A Company's Central Brain

An internal tool that prioritizes a company's work according to the **dependencies between people**: if your task
blocks others, its priority rises on its own, without anyone having to chase it. An urgency-1 request that holds up
four people ends up ranked above an "urgent" one that blocks nobody.

**What it solves.** In any company, what really holds a project back is often a small request sitting in another
department's backlog: a license, an access grant, an onboarding. Whoever owns it doesn't know who they are blocking,
and whoever is waiting can only nag by email. OrgBrain makes that chain visible and reprioritizes automatically.

Four building blocks:

| | |
|---|---|
| **Priority engine** | Propagates urgency through the dependency graph and records the reason for every bump |
| **Per-person assistant** | Creates tasks from natural language, assigns them to the right person, remembers each user's context and applies changes through conversation |
| **Project management** | Health, progress, team, milestones, risks and decisions, with its own mind map and graph |
| **Dependency graph** | The whole network of blockers, showing the full chain of a task when selected |

```
frontend/  React 19 + Vite + React Flow (simulated login, my tasks, assistant, projects, graph, directory)
backend/   Spring Boot 4.1 (Java 21) + JPA + XAMPP MySQL/MariaDB + Gemini API
```

> **Status: prototype (MVP v1).** It works end to end with **simulated data** (100 employees, 300 tasks,
> 72 dependencies, 8 projects) generated at startup. There is no real authentication (you pick a user from a list)
> and no integration with email, Teams, Slack or Jira. Nothing shown in the app corresponds to real people.

> **Note:** the UI, the seed data and the API field names (`peticiones`, `tareas_propias`, `urgencia`…) are in
> Spanish. Identifiers, routes and JSON keys are kept as they appear in the code.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 or higher | Tested with JDK 26 |
| Node.js | 20 or higher | Tested with Node 24 |
| XAMPP | with MySQL | XAMPP ships **MariaDB 10.4**, which is compatible |
| Maven | — | Not needed: the bundled `mvnw` wrapper is used |
| Gemini API key | optional | Free at https://aistudio.google.com/apikey |

## Getting Started

### 1. Database (XAMPP)

1. Open the XAMPP control panel and click **Start** on MySQL.
2. You don't need to create anything: on startup, the backend creates the `orgbrain` database, the tables
   (`schema.sql`) and the simulated data if it is empty.

If your MySQL doesn't use `root` without a password, set the `DB_USER` and `DB_PASSWORD` variables (see the table
below). If you prefer to create the schema by hand, import `backend/src/main/resources/schema.sql` from phpMyAdmin
(`http://localhost/phpmyadmin`) into a database called `orgbrain`.

### 2. Backend

1. Copy `.env.example` to `.env` (in the project root) and add your key: `GEMINI_API_KEY=...`.
   It is optional: without a key the chat works with simple rules. The `.env` file is not committed to the repository.
2. Start it:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

You can also pass the key as an environment variable (`$env:GEMINI_API_KEY = "..."` in PowerShell), which takes
precedence over `.env`, but only lasts while that terminal window stays open.

The first run takes a while (it downloads Maven and dependencies). When you see
`Seeder: 100 empleados, 17 dominios, 300 tareas, 72 dependencias` in the log, the API is ready at
`http://localhost:8080/api`.

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to the backend (port 8080), so there is no need to configure CORS.

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `GEMINI_API_KEY` | *(empty)* | Google AI Studio API key. Without it, the chat uses a rule-based extractor |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Gemini model (any model with a free tier and structured output) |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL/MariaDB server |
| `DB_NAME` | `orgbrain` | Created automatically if it doesn't exist |
| `DB_USER` / `DB_PASSWORD` | `root` / *(empty)* | Default XAMPP credentials |
| `SEED_ENABLED` | `true` | Generate simulated data if the database is empty |
| `SERVER_PORT` | `8080` | Backend port |
| `CORS_ORIGINS` | `http://localhost:5173` | Allowed origins if the frontend doesn't use the Vite proxy |
| `VITE_BACKEND_URL` | `http://localhost:8080` | (frontend) Where Vite proxies `/api` calls to |

### Regenerating the demo data

The seeder is idempotent: if employees already exist, it does nothing. To start from scratch, drop the database
(in phpMyAdmin, or with `C:\xampp\mysql\bin\mysql.exe -u root -e "DROP DATABASE orgbrain;"`) and restart the backend.
The random seed is fixed, so the same data is always generated.

## Project Management

Each project has an owner, a status (planning, in progress, at risk, paused, completed, cancelled), a priority from
1 to 5, dates, a team with role and allocation, milestones, and a log of decisions, risks, blockers and changes.
Tasks belong to a project and **the project's priority pushes its tasks up** in the engine
(priority 5: +0.5; priority 4: +0.25; at risk: +0.25).

The **Projects** tab shows each project's health at a glance and, when opened:

| View | What it gives you |
|---|---|
| Summary | Next steps (what can be done right now), top priorities, bottlenecks and dependencies on other projects |
| Mind map | Team, next steps, bottlenecks, milestones, risks and external dependencies around the project |
| Tasks | Board by status and creation of new tasks within the project |
| Team | Who works on it, with role, allocation, open tasks, how many are waiting on others and their total workload |
| Milestones | Timeline with overdue ones highlighted |
| Log | Open risks and blockers, decisions and the change history with its author |
| Dependencies | The graph, limited to the project and the outside tasks connected to it |

**Project health**: red if it is flagged at risk, the target date has passed, there are overdue milestones, there are
3 or more overdue tasks, or 50% or more of the open work is waiting on other tasks; yellow with any overdue task, open
risks, 25% or more waiting, a target date less than 14 days away with less than 70% done, or a paused project.

All changes (through the web or the chat) are recorded in the project log along with who made them. The login is
simulated, so anyone can edit any project: there are no permissions in this v1.

### From the chat

- "How is Orión doing?", "what's missing in Atlas?", "how are my projects?" → health, progress, priorities, next
  steps, bottlenecks, next milestone and risks.
- "Put Atlas at risk", "raise Nexo's priority to 5", "Fénix's target date moves to October 30".
- "Add Hugo Benítez to the Orión project as a backend developer at 40%", "remove X from the team",
  "Lince's owner is now Y".
- "Create the project Expansion to Portugal", "new milestone in Orión: dress rehearsal on Friday", "milestone X completed".
- "Risk in Atlas: the supplier isn't arriving", "we have decided…", "that risk is already resolved".
- "Mark #303 as done", "raise #12's urgency to 5", "hand #45 over to Laura", "put #77 in Orión".

## Demo Script (5 minutes)

1. **Log in as Laura Martín Sánchez** (Microsoft 365 administrator). Her first task, *"Assign GitHub Copilot licenses
   to the Orión team"*, has its own urgency of **2** but a calculated one of **6.4**: 🔺 it blocks 4 people.
   Click *Details* to see who it blocks.
2. **Dependency graph → My network**: you can see how that task blocks three developers and, down the chain, Ana's
   Orión demo. Switch to *Whole company* to see the long chains (migration to the Fénix ERP, 6 links).
3. **Switch to Ana López García** (head of the Orión project) → *Assistant*. Type:
   *"I'm developing the Orión project and I'm missing the Copilot license for the team"*.
   The assistant creates a task in Laura's backlog (M365 licenses owner), links it as a subtask that blocks Ana's
   Orión demo and saves the need to memory. The right-hand panel shows what it remembers about Ana (the seeder
   already gave her prior context about Orión and Lince).
4. Repeat the same message: it detects that the request already exists and doesn't duplicate it.
5. Try *"I need VPN access to work from home"* (goes to IT networking) or
   *"Create a subtask for me to prepare the demo slides within the Orión project"* (own task, linked).
6. **Go back to Laura** and mark the licenses task as *Done*: priorities are recalculated instantly and it stops
   blocking anyone.
7. **Projects**: open *Atlas* (shown in red). In *Summary* you'll see that what holds it back is the SSL
   certificate; in *Mind map*, the whole project at a glance; in *Team*, who is overloaded. Try asking the assistant
   "How is Atlas doing?" and "add [someone] to the Atlas project as an analyst".

Other seeder scenarios: **Javier Ortega**'s SSL certificate (urgency 1) blocks the launch of the Atlas portal for
Sales, Marketing and the PMO; the onboarding of an analyst by **Marta Gil** (HR, urgency 1) blocks Finance's quarterly
close; **Carmen Ruiz**'s translation budget blocks Marketing's internationalization.

## How It Works

### Reprioritization engine

`MotorPriorizacionService` walks the graph from the leaves upward (reverse topological order, Kahn's algorithm).
For each open task:

| Step | Calculation |
|---|---|
| Base | own urgency + due-date bonus (overdue +1, ≤ 3 days +0.5, ≤ 7 days +0.25) + project bonus (priority 5 +0.5, priority 4 +0.25, at risk +0.25), max. 5 |
| Inherited | maximum of the *level* of the tasks it directly blocks (×1 if the dependency is hard, ×0.6 if soft) |
| Level | max(base, inherited): this is what gets passed upward, so urgency propagates along long chains |
| Blocking bonus | +0.35 for each urgent task (level ≥ 4) blocked directly or transitively, +0.1 for the others, max. 2.5 |
| Calculated | level + bonus, between 1 and 10 |

**Done** tasks don't block. It also stores a human-readable reason (*"Inherits urgency 5.0 from «…» (Hugo Benítez) ·
Blocks 4 tasks (4 urgent) belonging to 4 people"*) and the counters shown by the interface. Everything is recalculated
after every task or dependency change (about 50 ms with 300 tasks) and on demand with
`POST /api/priorizacion/recalcular`. Creating a dependency is rejected if it would form a cycle.

### Chat, assignment and memory

Each message (`ChatService`) goes through these steps:

1. **Context**: retrieves the user's relevant memory (keyword and project matching, no embeddings in v1), their open
   tasks, the responsibilities directory and the last 10 messages.
2. **Structured extraction with Gemini** (`responseMimeType: application/json` + `responseSchema`):
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
   This is the requested schema, extended to support several requests, own tasks and memories in a single message,
   plus `dependencias` for relationships the user expresses between existing or new tasks ("without the license I
   can't do the briefing"). Gemini is also given the open requests the user has already made, with their `#id`.
3. **Assignment and links** (`AsignacionService`), in this order:
   1. Creates the requests (domain proposed by Gemini or, failing that, by keywords; owner is whoever has the fewest
      open tasks) and the own tasks. If they already existed they are not duplicated: they are reused and their
      urgency is raised if the new one is higher.
   2. Creates the explicit `dependencias`, resolved by `#id` or by title only among the user's tasks, their open
      requests and what was created in that message. They are rejected if they would create a cycle.
   3. Links to the project task (**hard dependency**) only what doesn't already block another task in the message,
      so the chain ends up as `data → script → rehearsal → demo` instead of hanging everything off the demo. The
      project matches `Proyecto: …` tasks (created if they don't exist) or, if it is a known named project, its tasks.
   The texts in Gemini's JSON are reviewed to recover accented letters that sometimes arrive corrupted.
4. **Memory**: stores what is relevant, avoiding repetition.
5. **Natural-language reply** with Gemini, confirming exactly the actions performed.

Gemini calls are made outside database transactions. **Without an API key, or if Gemini fails** (quota exhausted,
network...), the chat keeps working with `ExtractorHeuristico` (rules for typical phrases) and a templated reply. The
interface shows in every reply which mode was used.

## REST API

| Method | Route | Description |
|---|---|---|
| GET | `/api/empleados?q=` | Employees (simulated login) |
| GET | `/api/empleados/{id}/tareas?incluirHechas=` | The employee's tasks sorted by calculated urgency |
| GET | `/api/empleados/{id}/peticiones` | Tasks they have requested from other people |
| GET | `/api/empleados/{id}/memoria` | Context memory |
| GET, POST | `/api/tareas` | List / create task (recalculates priorities) |
| GET, PATCH | `/api/tareas/{id}` | View / modify (status, urgency, title, due date) |
| GET | `/api/tareas/{id}/dependencias` | What it blocks and who blocks it |
| GET, POST | `/api/dependencias` | List / create (validates cycles, recalculates) |
| DELETE | `/api/dependencias/{id}` | Delete (recalculates) |
| POST | `/api/priorizacion/recalcular` | On-demand recalculation |
| GET | `/api/grafo?empleadoId=&proyectoId=&soloConDependencias=` | Nodes and edges; can be filtered by person or project |
| GET | `/api/dominios` | Responsibilities directory |
| GET | `/api/proyectos` | Projects with health, progress and alerts |
| GET | `/api/proyectos/{id}` | Detail: tasks, priorities, next steps, bottlenecks, team, milestones and log |
| POST, PATCH | `/api/proyectos`, `/api/proyectos/{id}` | Create and modify |
| PUT, DELETE | `/api/proyectos/{id}/miembros[/{empleadoId}]` | Team |
| POST, PATCH | `/api/proyectos/{id}/hitos`, `/api/hitos/{id}` | Milestones |
| POST, PATCH | `/api/proyectos/{id}/notas`, `/api/notas/{id}` | Log and risk resolution |
| POST | `/api/chat/{empleadoId}/mensajes` | Send a message to the assistant `{ "mensaje": "..." }` |
| GET | `/api/chat/{empleadoId}/mensajes` | History |
| PATCH | `/api/memoria/{id}/archivar` | Mark a memory as no longer valid |
| GET | `/api/ia/estado` | Whether Gemini is configured and which model it uses |

Errors are returned as `ProblemDetail` (`{ "status", "title", "detail" }`). Writes accept the `X-Empleado-Id` header
(the simulated-login user), which is stored as the author in the project log.

## Backend Structure

```
backend/src/main/java/com/orgbrain
├── config/       GeminiProperties, CORS
├── controller/   REST
├── dto/          input/output records + DtoMapper
├── entity/       JPA entities and enums
├── exception/    errors → ProblemDetail
├── repository/   Spring Data JPA
├── seed/         simulated data, projects and migrations (CommandLineRunner, Datafaker)
├── service/      prioritization engine, chat, assignment, memory, tasks, dependencies
│   └── ia/       GeminiClient, ExtractorHeuristico
└── util/         text normalization and keywords
backend/src/main/resources
├── application.yml
└── schema.sql    schema compatible with MariaDB 10.4 / MySQL
```

Unit tests (engine, rule-based extractor, text): `.\mvnw.cmd test`.
Chat integration tests with a mocked Gemini (they need XAMPP; they use the `orgbrain_it` database):
`.\mvnw.cmd test "-Dit=true"`.

## Technical Notes

- **XAMPP's MariaDB 10.4**: Hibernate 7 only officially supports MariaDB ≥ 10.6 and generates `ALTER TABLE IF EXISTS`,
  which 10.4 doesn't understand. That's why the schema lives in `schema.sql` (Hibernate only validates it) and
  `MariaDBLegacyDialect` is used. The warning *"version 10.4 … no longer supported"* at startup is expected.
- **Known projects** (`orgbrain.proyectos` in `application.yml`): used by the seeder to generate task titles; the
  assistant recognizes the projects that exist in the database.
- **Automatic migration**: when starting on a database that predates project management, the `tarea.proyecto_id`
  column is added, the 8 projects are created, tasks are assigned to them by title, the umbrella "Proyecto: …" tasks
  the assistant used to create become real projects, and the texts Gemini returned with corrupted letters are
  repaired. It only happens if there are no projects yet.

## Out of Scope in v1 / Next Steps

- Integrations with email, Teams, Slack, Jira.
- Corporate authentication (SSO / Entra ID).
- Learning the "who does what" directory by observing patterns (in v1 it is fixed, from the seeder).
- Permissions: anyone can modify any project (the login is simulated), although everything is signed in the log.
- Memory with embeddings and semantic search (keywords in v1).
