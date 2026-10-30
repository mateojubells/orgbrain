package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "empleado", indexes = @Index(name = "idx_empleado_departamento", columnList = "departamento"))
public class Empleado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Departamento departamento;

	@Column(nullable = false, length = 100)
	private String rol;

	@Column(nullable = false, unique = true, length = 150)
	private String email;

	@Column(name = "fecha_alta", nullable = false)
	private LocalDate fechaAlta;

	@ManyToMany
	@JoinTable(name = "empleado_dominio",
			joinColumns = @JoinColumn(name = "empleado_id"),
			inverseJoinColumns = @JoinColumn(name = "dominio_id"))
	private Set<DominioResponsabilidad> dominios = new HashSet<>();

	protected Empleado() {
	}

	public Empleado(String nombre, Departamento departamento, String rol, String email, LocalDate fechaAlta) {
		this.nombre = nombre;
		this.departamento = departamento;
		this.rol = rol;
		this.email = email;
		this.fechaAlta = fechaAlta;
	}

	public Long getId() { return id; }
	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }
	public Departamento getDepartamento() { return departamento; }
	public void setDepartamento(Departamento departamento) { this.departamento = departamento; }
	public String getRol() { return rol; }
	public void setRol(String rol) { this.rol = rol; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public LocalDate getFechaAlta() { return fechaAlta; }
	public void setFechaAlta(LocalDate fechaAlta) { this.fechaAlta = fechaAlta; }
	public Set<DominioResponsabilidad> getDominios() { return dominios; }
}
