package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "hito", indexes = @Index(name = "idx_hito_proyecto", columnList = "proyecto_id"))
public class Hito {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proyecto_id", nullable = false)
	private Proyecto proyecto;

	@Column(nullable = false, length = 200)
	private String nombre;

	@Column(length = 500)
	private String descripcion;

	@Column(name = "fecha_objetivo")
	private LocalDate fechaObjetivo;

	@Column(nullable = false)
	private boolean completado;

	@Column(name = "fecha_completado")
	private LocalDate fechaCompletado;

	protected Hito() {
	}

	public Hito(Proyecto proyecto, String nombre, String descripcion, LocalDate fechaObjetivo) {
		this.proyecto = proyecto;
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.fechaObjetivo = fechaObjetivo;
	}

	public void completar(boolean completado) {
		this.completado = completado;
		this.fechaCompletado = completado ? LocalDate.now() : null;
	}

	public Long getId() { return id; }
	public Proyecto getProyecto() { return proyecto; }
	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }
	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
	public LocalDate getFechaObjetivo() { return fechaObjetivo; }
	public void setFechaObjetivo(LocalDate fechaObjetivo) { this.fechaObjetivo = fechaObjetivo; }
	public boolean isCompletado() { return completado; }
	public LocalDate getFechaCompletado() { return fechaCompletado; }
}
