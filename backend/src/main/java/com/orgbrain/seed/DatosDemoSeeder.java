package com.orgbrain.seed;

import com.orgbrain.entity.*;
import com.orgbrain.repository.*;
import com.orgbrain.service.MotorPriorizacionService;
import com.orgbrain.util.Texto;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Puebla la BD con datos simulados: 100 empleados, 17 dominios de responsabilidad, 300 tareas y ~72 dependencias.
 *
 * <p>Idempotente: si ya hay empleados no hace nada. Para regenerar, vacía la BD (ver README).
 * Usa una semilla fija, así que siempre genera los mismos datos.
 *
 * <p>Incluye escenarios de demo deliberados:
 * <ul>
 *   <li>Tareas de urgencia propia baja (1-2) que bloquean a varias tareas urgentes de otras personas
 *       (licencias Copilot, certificado SSL, alta de empleado, presupuesto de traducción, cuota de Azure).</li>
 *   <li>Cadenas largas de hasta 6 eslabones (migración ERP Fénix, app Lince, CRM Nexo).</li>
 *   <li>Memoria de contexto previa para Ana López (jefa del proyecto Orión).</li>
 * </ul>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "orgbrain.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DatosDemoSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DatosDemoSeeder.class);

	private static final int TOTAL_EMPLEADOS = 100;
	private static final int TOTAL_TAREAS = 300;
	private static final int DEPENDENCIAS_ALEATORIAS = 40;

	private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
			"septiembre", "octubre", "noviembre", "diciembre");
	private static final List<String> TRIMESTRES = List.of("Q1", "Q2", "Q3", "Q4");

	private final EmpleadoRepository empleadoRepository;
	private final DominioResponsabilidadRepository dominioRepository;
	private final TareaRepository tareaRepository;
	private final DependenciaRepository dependenciaRepository;
	private final MemoriaContextoRepository memoriaRepository;
	private final MotorPriorizacionService motor;
	private final long semilla;
	private final List<String> proyectos;

	private Random rnd;
	private Faker faker;
	private final Map<String, DominioResponsabilidad> dominios = new HashMap<>();
	private final Map<String, Empleado> personajes = new HashMap<>();
	private final Map<Departamento, List<Empleado>> porDepartamento = new EnumMap<>(Departamento.class);
	private final Set<String> emailsUsados = new HashSet<>();
	private int dependenciasCreadas;

	public DatosDemoSeeder(EmpleadoRepository empleadoRepository, DominioResponsabilidadRepository dominioRepository,
			TareaRepository tareaRepository, DependenciaRepository dependenciaRepository,
			MemoriaContextoRepository memoriaRepository, MotorPriorizacionService motor,
			@Value("${orgbrain.seed.random-seed:42}") long semilla, @Value("${orgbrain.proyectos}") List<String> proyectos) {
		this.empleadoRepository = empleadoRepository;
		this.dominioRepository = dominioRepository;
		this.tareaRepository = tareaRepository;
		this.dependenciaRepository = dependenciaRepository;
		this.memoriaRepository = memoriaRepository;
		this.motor = motor;
		this.semilla = semilla;
		this.proyectos = proyectos;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (empleadoRepository.count() > 0) {
			log.info("Seeder: la base de datos ya tiene datos, no se generan datos de demo");
			return;
		}
		rnd = new Random(semilla);
		faker = new Faker(Locale.of("es", "ES"), new Random(semilla));

		crearDominios();
		crearEmpleados();
		List<Tarea> escenarios = crearEscenariosDemo();
		List<Tarea> generadas = crearTareasGeneradas(TOTAL_TAREAS - escenarios.size());
		crearDependenciasAleatorias(generadas);
		crearMemoriaDemo();
		var resultado = motor.recalcularTodo();

		log.info("Seeder: {} empleados, {} dominios, {} tareas, {} dependencias. {} tareas suben de prioridad por dependencias",
				empleadoRepository.count(), dominioRepository.count(), tareaRepository.count(), dependenciasCreadas,
				resultado.tareasQueSubieron());
	}

	// ---------------------------------------------------------------- Dominios

	private void crearDominios() {
		dominio("LIC_M365", "Licencias Microsoft 365 y Copilot", TipoPeticion.LICENCIA,
				"Altas y bajas de licencias de Office, Teams, Outlook y Microsoft/GitHub Copilot",
				"licencia,microsoft,office,365,copilot,teams,outlook,excel,word,powerpoint,onedrive");
		dominio("LIC_SOFTWARE", "Licencias de software de desarrollo y diseño", TipoPeticion.LICENCIA,
				"JetBrains, Adobe, Figma, Visual Studio y otras herramientas con licencia",
				"licencia,jetbrains,intellij,adobe,photoshop,illustrator,figma,visual studio,autocad,postman,software");
		dominio("ACC_VPN", "Accesos VPN y red corporativa", TipoPeticion.ACCESO,
				"VPN, wifi corporativa, firewall y acceso remoto",
				"vpn,red,wifi,remoto,firewall,conexion,teletrabajo");
		dominio("ACC_SISTEMAS", "Accesos a servidores, bases de datos y cloud", TipoPeticion.ACCESO,
				"Permisos en Azure, AWS, servidores y bases de datos de cada entorno",
				"servidor,base de datos,azure,aws,cloud,sql,produccion,entorno,kubernetes,cuota");
		dominio("ACC_APLICACIONES", "Accesos a aplicaciones corporativas", TipoPeticion.ACCESO,
				"Permisos en ERP, CRM, SharePoint y carpetas compartidas",
				"erp,sap,crm,salesforce,sharepoint,carpeta compartida,intranet,nexo,fenix,jira,confluence");
		dominio("HW_EQUIPOS", "Equipos y hardware", TipoPeticion.OTRO,
				"Portátiles, monitores, móviles y periféricos",
				"portatil,ordenador,monitor,pantalla,raton,teclado,movil,hardware,cascos,docking");
		dominio("SOPORTE_IT", "Soporte técnico e incidencias", TipoPeticion.OTRO,
				"Incidencias, contraseñas bloqueadas, impresoras",
				"incidencia,error,funciona,contrasena,bloqueado,impresora,soporte,averia");
		dominio("RRHH_ALTAS", "Altas, bajas y onboarding", TipoPeticion.OTRO,
				"Contratación, incorporaciones y salidas de empleados",
				"alta,baja,contratacion,incorporacion,onboarding,nuevo empleado,becario,contrato laboral");
		dominio("RRHH_VACACIONES", "Vacaciones, permisos y ausencias", TipoPeticion.OTRO,
				"Solicitudes de vacaciones, permisos retribuidos y ausencias",
				"vacaciones,permiso,ausencia,baja medica,dias libres,festivo,horario");
		dominio("RRHH_NOMINAS", "Nóminas y certificados", TipoPeticion.INFORMACION,
				"Nóminas, certificados de empresa, retenciones",
				"nomina,certificado,salario,irpf,retencion,sueldo");
		dominio("RRHH_FORMACION", "Formación y certificaciones", TipoPeticion.OTRO,
				"Cursos, certificaciones y plan de formación",
				"formacion,curso,certificacion,training,idiomas,aprendizaje");
		dominio("FIN_COMPRAS", "Compras y proveedores", TipoPeticion.OTRO,
				"Pedidos, alta de proveedores y aprobación de presupuestos",
				"compra,proveedor,pedido,presupuesto,factura,contratar servicio,cotizacion");
		dominio("FIN_GASTOS", "Gastos y viajes", TipoPeticion.OTRO,
				"Notas de gastos, reservas de viaje y tarjetas corporativas",
				"gasto,viaje,dietas,reembolso,tarjeta,hotel,vuelo,kilometraje");
		dominio("FIN_INFORMES", "Informes financieros", TipoPeticion.INFORMACION,
				"Cierres contables, forecast y datos económicos",
				"informe financiero,cierre,contabilidad,forecast,cuentas,tesoreria,margen,rentabilidad");
		dominio("MKT_CONTENIDOS", "Marca y material de marketing", TipoPeticion.INFORMACION,
				"Logos, plantillas, presentaciones corporativas y contenidos web",
				"logo,marca,presentacion,plantilla,folleto,web,contenido,diseno,imagen corporativa");
		dominio("VEN_CLIENTES", "Datos de clientes y ofertas", TipoPeticion.INFORMACION,
				"Información de clientes, contratos y pipeline comercial",
				"cliente,oferta,contrato,pipeline,ventas,tarifa,precio,cuenta");
		dominio("PMO_PROYECTOS", "Planificación y recursos de proyectos", TipoPeticion.INFORMACION,
				"Estado de proyectos, hitos, asignación de recursos",
				"planificacion,roadmap,hito,recursos,cronograma,estado del proyecto,comite");
	}

	private void dominio(String codigo, String nombre, TipoPeticion tipo, String descripcion, String palabras) {
		dominios.put(codigo, dominioRepository.save(new DominioResponsabilidad(codigo, nombre, descripcion, tipo, palabras)));
	}

	// ---------------------------------------------------------------- Empleados

	private void crearEmpleados() {
		// Personajes fijos: expertos del directorio y protagonistas de los escenarios de demo.
		personaje("laura", "Laura Martín Sánchez", Departamento.IT, "Administradora de Microsoft 365", "LIC_M365");
		personaje("javier", "Javier Ortega Ruiz", Departamento.IT, "Administrador de redes y seguridad", "ACC_VPN");
		personaje("sergio", "Sergio Castillo Moreno", Departamento.IT, "Ingeniero cloud", "ACC_SISTEMAS");
		personaje("nuria", "Nuria Vidal Serrano", Departamento.IT, "Técnica de aplicaciones corporativas", "ACC_APLICACIONES", "LIC_SOFTWARE");
		personaje("pablo", "Pablo Romero Díaz", Departamento.IT, "Técnico de soporte", "HW_EQUIPOS", "SOPORTE_IT");
		personaje("irene", "Irene Prieto Molina", Departamento.IT, "Responsable de IT", "LIC_SOFTWARE");
		personaje("dev1", "Hugo Benítez Cano", Departamento.IT, "Desarrollador backend");
		personaje("dev2", "Sara Domínguez Pardo", Departamento.IT, "Desarrolladora full-stack");
		personaje("dev3", "Óscar Lorenzo Vargas", Departamento.IT, "Ingeniero QA");
		personaje("marta", "Marta Gil Herrero", Departamento.RRHH, "Técnica de selección y onboarding", "RRHH_ALTAS");
		personaje("elena", "Elena Navarro Campos", Departamento.RRHH, "Responsable de administración de personal", "RRHH_NOMINAS", "RRHH_VACACIONES");
		personaje("raul", "Raúl Herrera Vega", Departamento.RRHH, "Responsable de formación", "RRHH_FORMACION");
		personaje("carmen", "Carmen Ruiz Delgado", Departamento.FINANZAS, "Responsable de compras", "FIN_COMPRAS");
		personaje("alberto", "Alberto Fuentes León", Departamento.FINANZAS, "Controller financiero", "FIN_INFORMES");
		personaje("fin1", "Rocío Pastor Medina", Departamento.FINANZAS, "Analista de tesorería");
		personaje("lucia", "Lucía Méndez Rubio", Departamento.MARKETING, "Responsable de marca", "MKT_CONTENIDOS");
		personaje("mkt1", "Daniel Soto Crespo", Departamento.MARKETING, "Diseñador gráfico");
		personaje("mkt2", "Paula Rey Calvo", Departamento.MARKETING, "Responsable de campañas");
		personaje("diego", "Diego Santos Iglesias", Departamento.VENTAS, "Responsable de operaciones comerciales", "VEN_CLIENTES");
		personaje("ven1", "Andrés Marín Nieto", Departamento.VENTAS, "Director comercial");
		personaje("ven2", "Beatriz Cabrera Luna", Departamento.VENTAS, "Key account manager");
		personaje("ana", "Ana López García", Departamento.DIRECCION_PROYECTO, "Jefa de proyecto", "PMO_PROYECTOS");
		personaje("tomas", "Tomás Aguilar Peña", Departamento.DIRECCION_PROYECTO, "Director de la PMO", "PMO_PROYECTOS");

		// Resto con Datafaker hasta 100, con roles por departamento.
		Map<Departamento, Integer> objetivo = new EnumMap<>(Map.of(
				Departamento.IT, 20, Departamento.RRHH, 12, Departamento.MARKETING, 16,
				Departamento.VENTAS, 20, Departamento.FINANZAS, 14, Departamento.DIRECCION_PROYECTO, 18));
		// Segundos expertos para que haya reparto de carga dentro de un mismo dominio.
		generado(Departamento.IT, "Técnico/a de soporte", "SOPORTE_IT", "HW_EQUIPOS");
		generado(Departamento.IT, "Administrador/a de sistemas", "ACC_SISTEMAS", "ACC_VPN");
		generado(Departamento.IT, "Técnico/a de puesto de trabajo", "LIC_M365", "HW_EQUIPOS");
		generado(Departamento.RRHH, "Técnico/a de nóminas", "RRHH_NOMINAS");
		generado(Departamento.RRHH, "Técnico/a de relaciones laborales", "RRHH_VACACIONES", "RRHH_ALTAS");
		generado(Departamento.FINANZAS, "Técnico/a de contabilidad", "FIN_GASTOS");
		generado(Departamento.FINANZAS, "Administrativo/a de viajes y gastos", "FIN_GASTOS", "FIN_COMPRAS");
		generado(Departamento.MARKETING, "Community manager", "MKT_CONTENIDOS");
		generado(Departamento.VENTAS, "Analista de ventas", "VEN_CLIENTES");

		Map<Departamento, List<String>> roles = Map.of(
				Departamento.IT, List.of("Desarrollador/a backend", "Desarrollador/a frontend", "Ingeniero/a DevOps",
						"Arquitecto/a de software", "Analista de datos", "Ingeniero/a QA", "Desarrollador/a móvil"),
				Departamento.RRHH, List.of("Técnico/a de RRHH", "HR business partner", "Técnico/a de selección",
						"Responsable de bienestar laboral"),
				Departamento.MARKETING, List.of("Especialista SEO", "Redactor/a de contenidos", "Diseñador/a gráfico/a",
						"Analista de marketing digital", "Product marketing manager", "Responsable de eventos"),
				Departamento.VENTAS, List.of("Comercial", "Key account manager", "Preventa técnico/a",
						"Responsable de zona", "Customer success manager", "Inside sales"),
				Departamento.FINANZAS, List.of("Analista financiero/a", "Contable", "Técnico/a de facturación",
						"Analista de control de gestión", "Técnico/a fiscal"),
				Departamento.DIRECCION_PROYECTO, List.of("Jefe/a de proyecto", "Project manager junior", "Scrum master",
						"Analista de negocio", "PMO analyst", "Product owner"));

		for (Departamento dep : Departamento.values()) {
			List<String> rolesDep = roles.get(dep);
			while (porDepartamento.getOrDefault(dep, List.of()).size() < objetivo.get(dep)) {
				generado(dep, rolesDep.get(rnd.nextInt(rolesDep.size())));
			}
		}
		if (empleadoRepository.count() != TOTAL_EMPLEADOS) {
			throw new IllegalStateException("El reparto por departamentos no suma " + TOTAL_EMPLEADOS);
		}
	}

	private void personaje(String clave, String nombre, Departamento dep, String rol, String... codigosDominio) {
		personajes.put(clave, empleado(nombre, dep, rol, codigosDominio));
	}

	private void generado(Departamento dep, String rol, String... codigosDominio) {
		String nombre = faker.name().firstName() + " " + faker.name().lastName() + " " + faker.name().lastName();
		empleado(nombre, dep, rol, codigosDominio);
	}

	private Empleado empleado(String nombre, Departamento dep, String rol, String... codigosDominio) {
		LocalDate alta = LocalDate.of(2012, 1, 1).plusDays(rnd.nextInt(14 * 365));
		Empleado e = new Empleado(nombre, dep, rol, email(nombre), alta);
		for (String codigo : codigosDominio) {
			e.getDominios().add(dominios.get(codigo));
		}
		empleadoRepository.save(e);
		porDepartamento.computeIfAbsent(dep, k -> new ArrayList<>()).add(e);
		return e;
	}

	private String email(String nombre) {
		String[] partes = Texto.normalizar(nombre).split(" ");
		String base = partes[0] + "." + partes[1];
		String email = base + "@orgbrain.demo";
		for (int i = 2; !emailsUsados.add(email); i++) {
			email = base + i + "@orgbrain.demo";
		}
		return email;
	}

	// ---------------------------------------------------------------- Escenarios de demo

	private List<Tarea> crearEscenariosDemo() {
		List<Tarea> t = new ArrayList<>();

		// 1) Licencias Copilot (urgencia 2) bloquean al equipo de Orión y, en cadena, la demo al comité.
		Tarea copilot = tarea(t, "laura", "Asignar licencias de GitHub Copilot al equipo de Orión",
				"El equipo de desarrollo de Orión necesita Copilot activado en sus cuentas para empezar la fase de implementación.",
				2, EstadoTarea.PENDIENTE, null);
		Tarea orion1 = tarea(t, "dev1", "Implementar el módulo de recomendaciones de Orión",
				"Motor de recomendaciones para clientes: API y modelo inicial.", 5, EstadoTarea.EN_CURSO, 12);
		Tarea orion2 = tarea(t, "dev2", "Refactorizar la API de pedidos para Orión",
				"Adaptar la API de pedidos para que Orión pueda consumir el histórico.", 4, EstadoTarea.PENDIENTE, 15);
		Tarea orion3 = tarea(t, "dev3", "Automatizar pruebas end-to-end de Orión",
				"Suite de pruebas automáticas previa a la demo.", 4, EstadoTarea.PENDIENTE, 15);
		Tarea demoOrion = tarea(t, "ana", "Entregar la demo de Orión al comité de dirección",
				"Presentación funcional del proyecto Orión ante el comité.", 5, EstadoTarea.EN_CURSO, 5);
		dep(copilot, orion1, TipoDependencia.DURA);
		dep(copilot, orion2, TipoDependencia.DURA);
		dep(copilot, orion3, TipoDependencia.BLANDA);
		dep(orion1, demoOrion, TipoDependencia.DURA);
		dep(orion2, demoOrion, TipoDependencia.DURA);
		dep(orion3, demoOrion, TipoDependencia.BLANDA);

		// 2) Certificado SSL (urgencia 1) bloquea el lanzamiento del portal Atlas en tres departamentos.
		Tarea ssl = tarea(t, "javier", "Renovar el certificado SSL del portal de clientes",
				"El certificado caduca pronto y sin él no se puede abrir el portal a clientes externos.",
				1, EstadoTarea.PENDIENTE, null);
		Tarea atlasVentas = tarea(t, "ven2", "Lanzar el portal de clientes Atlas a las cuentas clave",
				"Comunicar y dar acceso a las 20 cuentas principales.", 5, EstadoTarea.PENDIENTE, 7);
		Tarea atlasLanding = tarea(t, "mkt2", "Publicar la landing de la campaña de lanzamiento de Atlas",
				"Landing con registro al portal y formulario de contacto.", 4, EstadoTarea.PENDIENTE, 9);
		Tarea atlasGoLive = tarea(t, "tomas", "Dar el go-live del proyecto Atlas",
				"Checklist final y paso a producción del portal.", 5, EstadoTarea.PENDIENTE, 6);
		Tarea atlasFormacion = tarea(t, "ven1", "Formar a los comerciales en el portal Atlas",
				"Sesión práctica con el equipo de ventas.", 4, EstadoTarea.PENDIENTE, 10);
		dep(ssl, atlasVentas, TipoDependencia.DURA);
		dep(ssl, atlasLanding, TipoDependencia.DURA);
		dep(ssl, atlasGoLive, TipoDependencia.DURA);
		dep(ssl, atlasFormacion, TipoDependencia.BLANDA);

		// 3) Alta en sistemas del nuevo analista (urgencia 1) bloquea el cierre financiero.
		Tarea alta = tarea(t, "marta", "Dar de alta en sistemas al nuevo analista financiero",
				"Crear usuario, correo y permisos básicos para la nueva incorporación de Finanzas.",
				1, EstadoTarea.PENDIENTE, null);
		Tarea conciliaciones = tarea(t, "fin1", "Preparar las conciliaciones bancarias del trimestre",
				"Tarea asignada al nuevo analista junto a Rocío.", 5, EstadoTarea.PENDIENTE, 4);
		Tarea tesoreria = tarea(t, "fin1", "Cerrar el informe de tesorería mensual",
				"Requiere el apoyo del nuevo analista con acceso al ERP.", 4, EstadoTarea.PENDIENTE, 8);
		Tarea cierre = tarea(t, "alberto", "Presentar el cierre trimestral a dirección",
				"Resultados trimestrales, desviaciones y previsión.", 5, EstadoTarea.PENDIENTE, 9);
		dep(alta, conciliaciones, TipoDependencia.DURA);
		dep(alta, tesoreria, TipoDependencia.DURA);
		dep(conciliaciones, cierre, TipoDependencia.DURA);
		dep(tesoreria, cierre, TipoDependencia.BLANDA);

		// 4) Presupuesto de traducción (urgencia 2) bloquea la internacionalización.
		Tarea presupuesto = tarea(t, "carmen", "Aprobar el presupuesto del proveedor de traducción",
				"Tres ofertas recibidas; falta validar y emitir el pedido.", 2, EstadoTarea.PENDIENTE, null);
		Tarea web = tarea(t, "lucia", "Traducir la web corporativa al inglés y al francés",
				"Traducción y revisión de las 40 páginas principales.", 4, EstadoTarea.PENDIENTE, 20);
		Tarea aurora = tarea(t, "mkt2", "Localizar la campaña Aurora para Francia",
				"Adaptar creatividades y copies de Aurora al mercado francés.", 5, EstadoTarea.PENDIENTE, 12);
		Tarea dossier = tarea(t, "ven1", "Preparar el dossier comercial para distribuidores europeos",
				"Dossier en inglés y francés para la red de distribución.", 4, EstadoTarea.PENDIENTE, 18);
		dep(presupuesto, web, TipoDependencia.DURA);
		dep(presupuesto, aurora, TipoDependencia.DURA);
		dep(presupuesto, dossier, TipoDependencia.DURA);

		// 5) Cuota de Azure (urgencia 2) bloquea la plataforma de datos Titán.
		Tarea azure = tarea(t, "sergio", "Ampliar la cuota de Azure del entorno de datos de Titán",
				"El entorno de datos se ha quedado sin cuota de cómputo.", 2, EstadoTarea.PENDIENTE, null);
		Tarea etl = tarea(t, "dev1", "Desplegar los procesos ETL de Titán",
				"Cargas nocturnas desde ERP y CRM.", 4, EstadoTarea.PENDIENTE, 14);
		Tarea cuadros = tarea(t, "alberto", "Publicar los cuadros de mando financieros de Titán",
				"Cuadros de margen y tesorería para dirección.", 4, EstadoTarea.PENDIENTE, 21);
		Tarea informeVentas = tarea(t, "diego", "Automatizar el informe semanal de ventas con Titán",
				"Sustituir el Excel manual por el informe automático.", 3, EstadoTarea.PENDIENTE, 25);
		dep(azure, etl, TipoDependencia.DURA);
		dep(etl, cuadros, TipoDependencia.DURA);
		dep(etl, informeVentas, TipoDependencia.DURA);

		// Cadena larga 1 (6 eslabones): migración al ERP Fénix.
		Tarea f1 = tarea(t, "nuria", "Exportar los maestros de proveedores del ERP antiguo", "Primer paso de la migración a Fénix.", 2, EstadoTarea.PENDIENTE, null);
		Tarea f2 = tarea(t, "carmen", "Depurar proveedores duplicados antes de la migración", "Revisar CIF duplicados y proveedores inactivos.", 2, EstadoTarea.PENDIENTE, null);
		Tarea f3 = tarea(t, "sergio", "Cargar los datos depurados en el nuevo ERP Fénix", "Carga en el entorno de preproducción y producción.", 3, EstadoTarea.PENDIENTE, null);
		Tarea f4 = tarea(t, "fin1", "Validar los saldos migrados en Fénix", "Cuadrar saldos de proveedores contra el ERP antiguo.", 3, EstadoTarea.PENDIENTE, null);
		Tarea f5 = tarea(t, "tomas", "Firmar el acta de arranque de Fénix", "Validación formal de la PMO para arrancar.", 4, EstadoTarea.PENDIENTE, null);
		Tarea f6 = tarea(t, "alberto", "Emitir las primeras facturas desde Fénix", "Facturación del mes desde el nuevo ERP.", 5, EstadoTarea.PENDIENTE, 3);
		dep(f1, f2, TipoDependencia.DURA);
		dep(f2, f3, TipoDependencia.DURA);
		dep(f3, f4, TipoDependencia.DURA);
		dep(f4, f5, TipoDependencia.DURA);
		dep(f5, f6, TipoDependencia.DURA);

		// Cadena larga 2 (5 eslabones): app móvil Lince.
		Tarea l1 = tarea(t, "mkt1", "Entregar la guía de estilo de la app Lince", "Colores, tipografías y componentes base.", 1, EstadoTarea.EN_CURSO, null);
		Tarea l2 = tarea(t, "dev2", "Maquetar las pantallas principales de Lince", "Login, inicio y perfil.", 3, EstadoTarea.PENDIENTE, null);
		Tarea l3 = tarea(t, "dev1", "Integrar el login corporativo en Lince", "Autenticación con cuentas corporativas.", 3, EstadoTarea.PENDIENTE, null);
		Tarea l4 = tarea(t, "dev3", "Pasar las pruebas de aceptación de Lince", "Pruebas con usuarios piloto.", 3, EstadoTarea.PENDIENTE, null);
		Tarea l5 = tarea(t, "ana", "Publicar Lince en las tiendas de aplicaciones", "Publicación en App Store y Google Play.", 5, EstadoTarea.PENDIENTE, 30);
		dep(l1, l2, TipoDependencia.DURA);
		dep(l2, l3, TipoDependencia.BLANDA);
		dep(l3, l4, TipoDependencia.DURA);
		dep(l4, l5, TipoDependencia.DURA);

		// Cadena larga 3 (4 eslabones): CRM Nexo.
		Tarea n1 = tarea(t, "diego", "Definir los campos obligatorios de oportunidad en Nexo", "Acordar con ventas qué datos son obligatorios.", 2, EstadoTarea.PENDIENTE, null);
		Tarea n2 = tarea(t, "nuria", "Configurar permisos de Nexo por equipo comercial", "Roles y visibilidad por zona.", 3, EstadoTarea.PENDIENTE, null);
		Tarea n3 = tarea(t, "ven2", "Migrar el pipeline de oportunidades a Nexo", "Pasar las oportunidades abiertas desde el Excel.", 4, EstadoTarea.PENDIENTE, null);
		Tarea n4 = tarea(t, "ven1", "Presentar el forecast trimestral desde Nexo", "Primer forecast oficial con datos del CRM.", 5, EstadoTarea.PENDIENTE, 11);
		dep(n1, n2, TipoDependencia.DURA);
		dep(n2, n3, TipoDependencia.DURA);
		dep(n3, n4, TipoDependencia.DURA);

		return t;
	}

	private Tarea tarea(List<Tarea> destino, String clavePersonaje, String titulo, String descripcion, int urgencia,
			EstadoTarea estado, Integer diasLimite) {
		Tarea t = nuevaTarea(personajes.get(clavePersonaje), titulo, descripcion, urgencia, estado);
		if (diasLimite != null) {
			t.setFechaLimite(LocalDate.now().plusDays(diasLimite));
		}
		tareaRepository.save(t);
		destino.add(t);
		return t;
	}

	private Tarea nuevaTarea(Empleado responsable, String titulo, String descripcion, int urgencia, EstadoTarea estado) {
		Tarea t = new Tarea(titulo, descripcion, responsable, urgencia);
		t.setEstado(estado);
		t.setOrigen(OrigenTarea.SEED);
		t.setFechaCreacion(LocalDateTime.now().minusDays(rnd.nextInt(60)).minusMinutes(rnd.nextInt(600)));
		return t;
	}

	private void dep(Tarea bloqueante, Tarea bloqueada, TipoDependencia tipo) {
		Dependencia d = new Dependencia(bloqueante, bloqueada, tipo);
		dependenciaRepository.save(d);
		dependenciasCreadas++;
	}

	// ---------------------------------------------------------------- Tareas generadas

	private List<Tarea> crearTareasGeneradas(int cantidad) {
		List<Empleado> todos = new ArrayList<>(empleadoRepository.findAll());
		Collections.shuffle(todos, rnd);
		List<Tarea> generadas = new ArrayList<>();
		for (int i = 0; i < cantidad; i++) {
			// Las primeras 100 garantizan que todo el mundo tenga al menos una tarea.
			Empleado responsable = i < todos.size() ? todos.get(i) : todos.get(rnd.nextInt(todos.size()));
			String[] plantilla = elegir(PLANTILLAS.get(responsable.getDepartamento()));
			Tarea t = nuevaTarea(responsable, rellenar(plantilla[0]), rellenar(plantilla[1]), urgenciaAleatoria(), estadoAleatorio());
			if (rnd.nextDouble() < 0.4) {
				t.setFechaLimite(LocalDate.now().plusDays(rnd.nextInt(46) - 5));
			}
			generadas.add(tareaRepository.save(t));
		}
		return generadas;
	}

	/**
	 * Aristas aleatorias solo entre tareas generadas, siempre de índice menor a mayor:
	 * así el subgrafo es acíclico por construcción y no se cruza con los escenarios.
	 */
	private void crearDependenciasAleatorias(List<Tarea> generadas) {
		Set<String> pares = new HashSet<>();
		int creadas = 0;
		int intentos = 0;
		while (creadas < DEPENDENCIAS_ALEATORIAS && intentos++ < 10_000) {
			int a = rnd.nextInt(generadas.size());
			int b = rnd.nextInt(generadas.size());
			if (a == b) {
				continue;
			}
			Tarea bloqueante = generadas.get(Math.min(a, b));
			Tarea bloqueada = generadas.get(Math.max(a, b));
			if (bloqueada.getEstado() == EstadoTarea.HECHA
					|| bloqueante.getResponsable().getId().equals(bloqueada.getResponsable().getId())
					|| !pares.add(bloqueante.getId() + "-" + bloqueada.getId())) {
				continue;
			}
			dep(bloqueante, bloqueada, rnd.nextDouble() < 0.7 ? TipoDependencia.DURA : TipoDependencia.BLANDA);
			creadas++;
		}
	}

	private int urgenciaAleatoria() {
		double r = rnd.nextDouble();
		return r < 0.15 ? 1 : r < 0.40 ? 2 : r < 0.70 ? 3 : r < 0.90 ? 4 : 5;
	}

	private EstadoTarea estadoAleatorio() {
		double r = rnd.nextDouble();
		return r < 0.5 ? EstadoTarea.PENDIENTE : r < 0.8 ? EstadoTarea.EN_CURSO : EstadoTarea.HECHA;
	}

	private <T> T elegir(List<T> lista) {
		return lista.get(rnd.nextInt(lista.size()));
	}

	private String rellenar(String plantilla) {
		String proyecto = elegir(proyectos);
		String otroProyecto = elegir(proyectos.stream().filter(p -> !p.equals(proyecto)).toList());
		return plantilla
				.replace("{proyecto}", proyecto)
				.replace("{proyecto2}", otroProyecto)
				.replace("{cliente}", faker.company().name())
				.replace("{persona}", faker.name().firstName() + " " + faker.name().lastName())
				.replace("{mes}", elegir(MESES))
				.replace("{trimestre}", elegir(TRIMESTRES))
				.replace("{anio}", String.valueOf(LocalDate.now().getYear() + 1))
				.replace("{producto}", elegir(List.of("OrgBrain Cloud", "la suite de analítica", "la app Lince", "el portal Atlas", "el servicio premium", "la nueva tarifa pyme")))
				.replace("{sistema}", elegir(List.of("el ERP", "el CRM", "la intranet", "el portal de clientes", "el data warehouse", "el servidor de ficheros")))
				.replace("{entorno}", elegir(List.of("de producción", "de preproducción", "de desarrollo")))
				.replace("{software}", elegir(List.of("JetBrains", "Adobe Creative Cloud", "Postman", "Figma", "Atlassian")))
				.replace("{departamento}", elegir(List.of("IT", "RRHH", "Marketing", "Ventas", "Finanzas", "la PMO")))
				.replace("{puesto}", elegir(List.of("desarrollador/a backend", "comercial senior", "analista financiero/a", "diseñador/a UX", "técnico/a de soporte")))
				.replace("{tema}", elegir(List.of("inteligencia artificial en la empresa", "productividad", "casos de éxito", "transformación digital")))
				.replace("{feria}", elegir(List.of("Mobile World Congress", "Digital Enterprise Show", "SIMO Educación", "BizBarcelona")))
				.replace(" de el ", " del ");
	}

	/** [título, descripción] por departamento. */
	private static final Map<Departamento, List<String[]>> PLANTILLAS = Map.of(
			Departamento.IT, List.of(
					new String[]{"Aplicar parches de seguridad en los servidores {entorno}", "Parches críticos del último boletín de seguridad."},
					new String[]{"Revisar las alertas de monitorización {entorno}", "Analizar alertas recurrentes y ajustar umbrales."},
					new String[]{"Documentar la arquitectura del proyecto {proyecto}", "Diagramas y decisiones técnicas en la wiki."},
					new String[]{"Corregir las incidencias abiertas del proyecto {proyecto}", "Priorizar y resolver los bugs reportados."},
					new String[]{"Actualizar la base de datos de {sistema}", "Migración a la versión soportada."},
					new String[]{"Configurar copias de seguridad de {sistema}", "Backups diarios y prueba de restauración."},
					new String[]{"Revisar pull requests pendientes de {proyecto}", "Revisión de código acumulada del sprint."},
					new String[]{"Optimizar consultas lentas en {sistema}", "Consultas con más de 2 segundos de respuesta."},
					new String[]{"Preparar el entorno de pruebas para {proyecto}", "Entorno aislado con datos anonimizados."},
					new String[]{"Renovar las licencias de {software}", "Revisar uso real antes de renovar."}),
			Departamento.RRHH, List.of(
					new String[]{"Organizar el onboarding de {persona}", "Plan de acogida de la primera semana."},
					new String[]{"Revisar las solicitudes de vacaciones de {mes}", "Validar solapes en equipos pequeños."},
					new String[]{"Preparar el plan de formación de {trimestre}", "Detección de necesidades por departamento."},
					new String[]{"Publicar la oferta de empleo de {puesto}", "Descripción del puesto y difusión en portales."},
					new String[]{"Actualizar la política de teletrabajo", "Incorporar los cambios acordados con el comité."},
					new String[]{"Coordinar la evaluación del desempeño de {departamento}", "Calendario de entrevistas y formularios."},
					new String[]{"Tramitar la baja voluntaria de {persona}", "Finiquito, devolución de material y bajas de accesos."},
					new String[]{"Revisar las incidencias de nómina de {mes}", "Diferencias reportadas por los empleados."}),
			Departamento.MARKETING, List.of(
					new String[]{"Preparar la campaña de {producto} para {trimestre}", "Mensajes, canales y presupuesto."},
					new String[]{"Redactar artículos del blog sobre {tema}", "Dos artículos optimizados para SEO."},
					new String[]{"Diseñar creatividades para redes sociales de {producto}", "Piezas para LinkedIn e Instagram."},
					new String[]{"Analizar los resultados de la campaña de {mes}", "Informe de conversiones y coste por lead."},
					new String[]{"Organizar la presencia en {feria}", "Stand, material y agenda de reuniones."},
					new String[]{"Actualizar el kit de prensa de {proyecto}", "Notas de prensa e imágenes actualizadas."},
					new String[]{"Preparar la newsletter de {mes}", "Contenidos y segmentación de la base de datos."}),
			Departamento.VENTAS, List.of(
					new String[]{"Enviar la propuesta comercial a {cliente}", "Propuesta económica y técnica personalizada."},
					new String[]{"Negociar la renovación del contrato con {cliente}", "Condiciones y descuentos por volumen."},
					new String[]{"Preparar la demo de producto para {cliente}", "Demo adaptada a su sector."},
					new String[]{"Actualizar el pipeline de oportunidades de {trimestre}", "Revisar probabilidades y fechas de cierre."},
					new String[]{"Visitar a {cliente} para presentar {producto}", "Reunión presencial con el equipo de compras."},
					new String[]{"Resolver la reclamación de facturación de {cliente}", "Revisar con Finanzas el abono correspondiente."}),
			Departamento.FINANZAS, List.of(
					new String[]{"Conciliar las cuentas bancarias de {mes}", "Cuadre de movimientos y partidas pendientes."},
					new String[]{"Revisar las facturas pendientes de {cliente}", "Reclamar cobros vencidos."},
					new String[]{"Preparar el presupuesto de {departamento} para {anio}", "Primera versión con los responsables."},
					new String[]{"Liquidar los gastos de viaje de {departamento}", "Revisión de tickets y reembolsos."},
					new String[]{"Calcular las provisiones del cierre de {mes}", "Provisiones de facturas pendientes de recibir."},
					new String[]{"Revisar el forecast de tesorería de {trimestre}", "Actualizar cobros y pagos previstos."}),
			Departamento.DIRECCION_PROYECTO, List.of(
					new String[]{"Actualizar la planificación del proyecto {proyecto}", "Replanificar hitos tras el último comité."},
					new String[]{"Preparar el informe de estado semanal de {proyecto}", "Avance, riesgos y próximos pasos."},
					new String[]{"Revisar los riesgos del proyecto {proyecto}", "Actualizar la matriz de riesgos."},
					new String[]{"Convocar el comité de seguimiento de {proyecto}", "Agenda y documentación previa."},
					new String[]{"Reasignar recursos entre {proyecto} y {proyecto2}", "Equilibrar la carga de los equipos."},
					new String[]{"Cerrar las lecciones aprendidas de {proyecto}", "Sesión retrospectiva y documento final."}));

	// ---------------------------------------------------------------- Memoria de demo

	private void crearMemoriaDemo() {
		Empleado ana = personajes.get("ana");
		memoria(ana, TipoMemoria.PROYECTO_ACTUAL, "Orión",
				"Lidera el proyecto Orión: plataforma de recomendaciones para clientes, con demo ante el comité de dirección en pocos días.");
		memoria(ana, TipoMemoria.DECISION, "Orión",
				"Ha decidido que el equipo de desarrollo de Orión use GitHub Copilot para acelerar la entrega.");
		memoria(ana, TipoMemoria.PROYECTO_ACTUAL, "Lince",
				"También es responsable de publicar la app móvil Lince en las tiendas de aplicaciones.");
	}

	private void memoria(Empleado empleado, TipoMemoria tipo, String proyecto, String contenido) {
		Set<String> palabras = Texto.tokens(contenido + " " + proyecto);
		memoriaRepository.save(new MemoriaContexto(empleado, contenido, tipo, proyecto, Texto.unirPalabrasClave(palabras)));
	}
}
