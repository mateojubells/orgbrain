package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarea", indexes = {
		@Index(name = "idx_tarea_responsable", columnList = "responsable_id"),
		@Index(name = "idx_tarea_urgencia_calculada", columnList = "urgencia_calculada"),
		@Index(name = "idx_tarea_estado", columnList = "estado"),
		@Index(name = "idx_tarea_proyecto", columnList = "proyecto_id")
})
public class Tarea {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String titulo;

	@Column(columnDefinition = "TEXT")
	private String descripcion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responsable_id", nullable = false)
	private Empleado responsable;

	/** Quién pidió la tarea (p. ej. el usuario del chat). Null en tareas propias. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitante_id")
	private Empleado solicitante;

	/** Proyecto al que pertenece (null si es trabajo suelto). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "proyecto_id")
	private Proyecto proyecto;

	/** Tarea de proyecto de la que esta es subtarea. La relación de bloqueo real va en Dependencia. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tarea_padre_id")
	private Tarea tareaPadre;

	@Column(name = "urgencia_propia", nullable = false)
	private int urgenciaPropia;

	/** Resultado del motor de repriorización. Puede superar a urgenciaPropia por herencia. */
	@Column(name = "urgencia_calculada", nullable = false)
	private double urgenciaCalculada;

	/** Explicación legible de por qué subió, p. ej. "Bloquea a 3 tareas urgentes de 3 personas". */
	@Column(name = "motivo_urgencia", length = 500)
	private String motivoUrgencia;

	/** Tareas abiertas bloqueadas directa o transitivamente por esta (calculado por el motor). */
	@Column(name = "tareas_bloqueadas", nullable = false)
	private int tareasBloqueadas;

	/** Personas distintas (sin contar al responsable) cuyas tareas bloquea esta (calculado por el motor). */
	@Column(name = "personas_bloqueadas", nullable = false)
	private int personasBloqueadas;

	/** Tareas abiertas que bloquean directamente a esta (calculado por el motor). */
	@Column(name = "bloqueantes_pendientes", nullable = false)
	private int bloqueantesPendientes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoTarea estado = EstadoTarea.PENDIENTE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrigenTarea origen = OrigenTarea.MANUAL;

	@Column(name = "fecha_creacion", nullable = false, updatable = false)
	private LocalDateTime fechaCreacion;

	@Column(name = "fecha_actualizacion", nullable = false)
	private LocalDateTime fechaActualizacion;

	@Column(name = "fecha_limite")
	private LocalDate fechaLimite;

	protected Tarea() {
	}

	public Tarea(String titulo, String descripcion, Empleado responsable, int urgenciaPropia) {
		this.titulo = titulo;
		this.descripcion = descripcion;
		this.responsable = responsable;
		this.urgenciaPropia = urgenciaPropia;
		this.urgenciaCalculada = urgenciaPropia;
	}

	@PrePersist
	void alCrear() {
		LocalDateTime ahora = LocalDateTime.now();
		if (fechaCreacion == null) {
			fechaCreacion = ahora;
		}
		fechaActualizacion = ahora;
	}

	@PreUpdate
	void alActualizar() {
		fechaActualizacion = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public String getTitulo() { return titulo; }
	public void setTitulo(String titulo) { this.titulo = titulo; }
	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
	public Empleado getResponsable() { return responsable; }
	public void setResponsable(Empleado responsable) { this.responsable = responsable; }
	public Empleado getSolicitante() { return solicitante; }
	public void setSolicitante(Empleado solicitante) { this.solicitante = solicitante; }
	public Proyecto getProyecto() { return proyecto; }
	public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }
	public Tarea getTareaPadre() { return tareaPadre; }
	public void setTareaPadre(Tarea tareaPadre) { this.tareaPadre = tareaPadre; }
	public int getUrgenciaPropia() { return urgenciaPropia; }
	public void setUrgenciaPropia(int urgenciaPropia) { this.urgenciaPropia = urgenciaPropia; }
	public double getUrgenciaCalculada() { return urgenciaCalculada; }
	public void setUrgenciaCalculada(double urgenciaCalculada) { this.urgenciaCalculada = urgenciaCalculada; }
	public String getMotivoUrgencia() { return motivoUrgencia; }
	public void setMotivoUrgencia(String motivoUrgencia) { this.motivoUrgencia = motivoUrgencia; }
	public int getTareasBloqueadas() { return tareasBloqueadas; }
	public void setTareasBloqueadas(int tareasBloqueadas) { this.tareasBloqueadas = tareasBloqueadas; }
	public int getPersonasBloqueadas() { return personasBloqueadas; }
	public void setPersonasBloqueadas(int personasBloqueadas) { this.personasBloqueadas = personasBloqueadas; }
	public int getBloqueantesPendientes() { return bloqueantesPendientes; }
	public void setBloqueantesPendientes(int bloqueantesPendientes) { this.bloqueantesPendientes = bloqueantesPendientes; }
	public EstadoTarea getEstado() { return estado; }
	public void setEstado(EstadoTarea estado) { this.estado = estado; }
	public OrigenTarea getOrigen() { return origen; }
	public void setOrigen(OrigenTarea origen) { this.origen = origen; }
	public LocalDateTime getFechaCreacion() { return fechaCreacion; }
	public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
	public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
	public LocalDate getFechaLimite() { return fechaLimite; }
	public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
}
