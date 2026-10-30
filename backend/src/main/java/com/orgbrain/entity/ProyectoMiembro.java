package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Persona asignada formalmente a un proyecto, con su papel y dedicación. */
@Entity
@Table(name = "proyecto_miembro",
		uniqueConstraints = @UniqueConstraint(name = "uk_proyecto_miembro", columnNames = {"proyecto_id", "empleado_id"}),
		indexes = @Index(name = "idx_miembro_empleado", columnList = "empleado_id"))
public class ProyectoMiembro {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proyecto_id", nullable = false)
	private Proyecto proyecto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "empleado_id", nullable = false)
	private Empleado empleado;

	@Column(name = "rol_proyecto", length = 120)
	private String rolProyecto;

	/** Porcentaje de su jornada dedicado al proyecto (0-100). */
	@Column(nullable = false)
	private int dedicacion;

	@Column(name = "fecha_alta", nullable = false)
	private LocalDate fechaAlta;

	protected ProyectoMiembro() {
	}

	public ProyectoMiembro(Proyecto proyecto, Empleado empleado, String rolProyecto, int dedicacion) {
		this.proyecto = proyecto;
		this.empleado = empleado;
		this.rolProyecto = rolProyecto;
		this.dedicacion = dedicacion;
	}

	@PrePersist
	void alCrear() {
		if (fechaAlta == null) {
			fechaAlta = LocalDate.now();
		}
	}

	public Long getId() { return id; }
	public Proyecto getProyecto() { return proyecto; }
	public Empleado getEmpleado() { return empleado; }
	public String getRolProyecto() { return rolProyecto; }
	public void setRolProyecto(String rolProyecto) { this.rolProyecto = rolProyecto; }
	public int getDedicacion() { return dedicacion; }
	public void setDedicacion(int dedicacion) { this.dedicacion = dedicacion; }
	public LocalDate getFechaAlta() { return fechaAlta; }
}
