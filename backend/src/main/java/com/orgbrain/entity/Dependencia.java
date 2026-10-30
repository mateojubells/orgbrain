package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Arista del grafo: {@code bloqueante} impide avanzar a {@code bloqueada}. */
@Entity
@Table(name = "dependencia",
		uniqueConstraints = @UniqueConstraint(name = "uk_dependencia_par", columnNames = {"tarea_bloqueante_id", "tarea_bloqueada_id"}),
		indexes = @Index(name = "idx_dependencia_bloqueada", columnList = "tarea_bloqueada_id"))
public class Dependencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tarea_bloqueante_id", nullable = false)
	private Tarea bloqueante;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tarea_bloqueada_id", nullable = false)
	private Tarea bloqueada;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TipoDependencia tipo;

	@Column(name = "fecha_creacion", nullable = false, updatable = false)
	private LocalDateTime fechaCreacion;

	protected Dependencia() {
	}

	public Dependencia(Tarea bloqueante, Tarea bloqueada, TipoDependencia tipo) {
		this.bloqueante = bloqueante;
		this.bloqueada = bloqueada;
		this.tipo = tipo;
	}

	@PrePersist
	void alCrear() {
		if (fechaCreacion == null) {
			fechaCreacion = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public Tarea getBloqueante() { return bloqueante; }
	public Tarea getBloqueada() { return bloqueada; }
	public TipoDependencia getTipo() { return tipo; }
	public void setTipo(TipoDependencia tipo) { this.tipo = tipo; }
	public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
