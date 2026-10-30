package com.orgbrain.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/** Tipo de gestión que resuelve alguien en la organización (directorio "quién hace qué"). */
@Entity
@Table(name = "dominio_responsabilidad")
public class DominioResponsabilidad {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Código estable, p. ej. LIC_M365 o ACC_VPN. Lo usa el seeder para ser idempotente. */
	@Column(nullable = false, unique = true, length = 50)
	private String codigo;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 500)
	private String descripcion;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_peticion", nullable = false, length = 20)
	private TipoPeticion tipoPeticion;

	/** Palabras clave separadas por comas para casar el "objeto" extraído por Gemini (v1 sin embeddings). */
	@Column(name = "palabras_clave", length = 500)
	private String palabrasClave;

	@ManyToMany(mappedBy = "dominios")
	private Set<Empleado> responsables = new HashSet<>();

	protected DominioResponsabilidad() {
	}

	public DominioResponsabilidad(String codigo, String nombre, String descripcion, TipoPeticion tipoPeticion, String palabrasClave) {
		this.codigo = codigo;
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.tipoPeticion = tipoPeticion;
		this.palabrasClave = palabrasClave;
	}

	public Long getId() { return id; }
	public String getCodigo() { return codigo; }
	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }
	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
	public TipoPeticion getTipoPeticion() { return tipoPeticion; }
	public void setTipoPeticion(TipoPeticion tipoPeticion) { this.tipoPeticion = tipoPeticion; }
	public String getPalabrasClave() { return palabrasClave; }
	public void setPalabrasClave(String palabrasClave) { this.palabrasClave = palabrasClave; }
	public Set<Empleado> getResponsables() { return responsables; }
}
