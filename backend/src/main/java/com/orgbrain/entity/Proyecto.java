package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "proyecto", indexes = {
		@Index(name = "idx_proyecto_responsable", columnList = "responsable_id"),
		@Index(name = "idx_proyecto_estado", columnList = "estado")
})
public class Proyecto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 150)
	private String nombre;

	@Column(columnDefinition = "TEXT")
	private String descripcion;

	/** Resultado que se quiere conseguir, en una frase. */
	@Column(length = 500)
	private String objetivo;

	/** Jefe/a de proyecto. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responsable_id", nullable = false)
	private Empleado responsable;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoProyecto estado = EstadoProyecto.EN_CURSO;

	/** 1-5. Se transmite a la urgencia de sus tareas en el motor de repriorización. */
	@Column(nullable = false)
	private int prioridad = 3;

	@Column(name = "fecha_inicio")
	private LocalDate fechaInicio;

	@Column(name = "fecha_objetivo")
	private LocalDate fechaObjetivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrigenTarea origen = OrigenTarea.MANUAL;

	@Column(name = "fecha_creacion", nullable = false, updatable = false)
	private LocalDateTime fechaCreacion;

	@Column(name = "fecha_actualizacion", nullable = false)
	private LocalDateTime fechaActualizacion;

	protected Proyecto() {
	}

	public Proyecto(String nombre, Empleado responsable) {
		this.nombre = nombre;
		this.responsable = responsable;
	}

	@PrePersist
	void alCrear() {
		LocalDateTime ahora = LocalDateTime.now();
		if (fechaCreacion == null) {
			fechaCreacion = ahora;
		}
		if (fechaInicio == null) {
			fechaInicio = ahora.toLocalDate();
		}
		fechaActualizacion = ahora;
	}

	@PreUpdate
	void alActualizar() {
		fechaActualizacion = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }
	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
	public String getObjetivo() { return objetivo; }
	public void setObjetivo(String objetivo) { this.objetivo = objetivo; }
	public Empleado getResponsable() { return responsable; }
	public void setResponsable(Empleado responsable) { this.responsable = responsable; }
	public EstadoProyecto getEstado() { return estado; }
	public void setEstado(EstadoProyecto estado) { this.estado = estado; }
	public int getPrioridad() { return prioridad; }
	public void setPrioridad(int prioridad) { this.prioridad = prioridad; }
	public LocalDate getFechaInicio() { return fechaInicio; }
	public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
	public LocalDate getFechaObjetivo() { return fechaObjetivo; }
	public void setFechaObjetivo(LocalDate fechaObjetivo) { this.fechaObjetivo = fechaObjetivo; }
	public OrigenTarea getOrigen() { return origen; }
	public void setOrigen(OrigenTarea origen) { this.origen = origen; }
	public LocalDateTime getFechaCreacion() { return fechaCreacion; }
	public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
