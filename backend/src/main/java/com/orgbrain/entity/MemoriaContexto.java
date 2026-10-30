package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Extracto útil de lo que el usuario ha contado al asistente.
 * v1: sin embeddings; la recuperación se hace por palabras clave y proyecto.
 */
@Entity
@Table(name = "memoria_contexto", indexes = @Index(name = "idx_memoria_empleado", columnList = "empleado_id"))
public class MemoriaContexto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "empleado_id", nullable = false)
	private Empleado empleado;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String contenido;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TipoMemoria tipo;

	@Column(name = "proyecto_relacionado", length = 200)
	private String proyectoRelacionado;

	/** Términos normalizados separados por comas para la búsqueda de relevancia. */
	@Column(name = "palabras_clave", length = 500)
	private String palabrasClave;

	/** false cuando una necesidad ya se resolvió o una decisión quedó obsoleta. */
	@Column(nullable = false)
	private boolean activa = true;

	@Column(nullable = false)
	private LocalDateTime fecha;

	protected MemoriaContexto() {
	}

	public MemoriaContexto(Empleado empleado, String contenido, TipoMemoria tipo, String proyectoRelacionado, String palabrasClave) {
		this.empleado = empleado;
		this.contenido = contenido;
		this.tipo = tipo;
		this.proyectoRelacionado = proyectoRelacionado;
		this.palabrasClave = palabrasClave;
	}

	@PrePersist
	void alCrear() {
		if (fecha == null) {
			fecha = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public Empleado getEmpleado() { return empleado; }
	public String getContenido() { return contenido; }
	public void setContenido(String contenido) { this.contenido = contenido; }
	public TipoMemoria getTipo() { return tipo; }
	public String getProyectoRelacionado() { return proyectoRelacionado; }
	public String getPalabrasClave() { return palabrasClave; }
	public boolean isActiva() { return activa; }
	public void setActiva(boolean activa) { this.activa = activa; }
	public LocalDateTime getFecha() { return fecha; }
}
