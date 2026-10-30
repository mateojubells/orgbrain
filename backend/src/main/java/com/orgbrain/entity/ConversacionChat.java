package com.orgbrain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Historial crudo del chat. Lo útil y resumido va en {@link MemoriaContexto}. */
@Entity
@Table(name = "conversacion_chat", indexes = @Index(name = "idx_chat_empleado_fecha", columnList = "empleado_id, fecha_hora"))
public class ConversacionChat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "empleado_id", nullable = false)
	private Empleado empleado;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String mensaje;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private RolMensaje rol;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	protected ConversacionChat() {
	}

	public ConversacionChat(Empleado empleado, String mensaje, RolMensaje rol) {
		this.empleado = empleado;
		this.mensaje = mensaje;
		this.rol = rol;
	}

	@PrePersist
	void alCrear() {
		if (fechaHora == null) {
			fechaHora = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public Empleado getEmpleado() { return empleado; }
	public String getMensaje() { return mensaje; }
	public RolMensaje getRol() { return rol; }
	public LocalDateTime getFechaHora() { return fechaHora; }
}
