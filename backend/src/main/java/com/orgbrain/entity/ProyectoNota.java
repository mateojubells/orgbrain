package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Entrada del diario del proyecto: decisiones, riesgos, bloqueos, notas y cambios registrados. */
@Entity
@Table(name = "proyecto_nota", indexes = @Index(name = "idx_nota_proyecto_fecha", columnList = "proyecto_id, fecha"))
public class ProyectoNota {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proyecto_id", nullable = false)
	private Proyecto proyecto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "autor_id")
	private Empleado autor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoNotaProyecto tipo;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String contenido;

	/** Solo para riesgos y bloqueos: false cuando ya está resuelto. */
	@Column(nullable = false)
	private boolean abierta = true;

	@Column(nullable = false)
	private LocalDateTime fecha;

	protected ProyectoNota() {
	}

	public ProyectoNota(Proyecto proyecto, Empleado autor, TipoNotaProyecto tipo, String contenido) {
		this.proyecto = proyecto;
		this.autor = autor;
		this.tipo = tipo;
		this.contenido = contenido;
		this.abierta = tipo == TipoNotaProyecto.RIESGO || tipo == TipoNotaProyecto.BLOQUEO;
	}

	@PrePersist
	void alCrear() {
		if (fecha == null) {
			fecha = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public Proyecto getProyecto() { return proyecto; }
	public Empleado getAutor() { return autor; }
	public TipoNotaProyecto getTipo() { return tipo; }
	public String getContenido() { return contenido; }
	public boolean isAbierta() { return abierta; }
	public void setAbierta(boolean abierta) { this.abierta = abierta; }
	public LocalDateTime getFecha() { return fecha; }
	public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
