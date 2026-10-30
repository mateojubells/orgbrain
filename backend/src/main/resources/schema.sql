-- OrgBrain MVP v1 - esquema de base de datos
-- Pensado para MariaDB 10.4 (XAMPP). Las migraciones del final usan "ADD COLUMN IF NOT EXISTS", propio de MariaDB.
-- Se ejecuta automáticamente al arrancar (spring.sql.init.mode=always) y es idempotente.
-- También puede importarse a mano desde phpMyAdmin sobre la base de datos `orgbrain`.

CREATE TABLE IF NOT EXISTS empleado (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    nombre       VARCHAR(120) NOT NULL,
    departamento VARCHAR(30)  NOT NULL,
    rol          VARCHAR(100) NOT NULL,
    email        VARCHAR(150) NOT NULL,
    fecha_alta   DATE         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_empleado_email UNIQUE (email),
    KEY idx_empleado_departamento (departamento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dominio_responsabilidad (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo         VARCHAR(50)  NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    descripcion    VARCHAR(500) NULL,
    tipo_peticion  VARCHAR(20)  NOT NULL,
    palabras_clave VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dominio_codigo UNIQUE (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS empleado_dominio (
    empleado_id BIGINT NOT NULL,
    dominio_id  BIGINT NOT NULL,
    PRIMARY KEY (empleado_id, dominio_id),
    KEY idx_empleado_dominio_dominio (dominio_id),
    CONSTRAINT fk_empleado_dominio_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    CONSTRAINT fk_empleado_dominio_dominio FOREIGN KEY (dominio_id) REFERENCES dominio_responsabilidad (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS proyecto (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    nombre              VARCHAR(150) NOT NULL,
    descripcion         TEXT         NULL,
    objetivo            VARCHAR(500) NULL,
    responsable_id      BIGINT       NOT NULL,
    estado              VARCHAR(20)  NOT NULL,
    prioridad           INT          NOT NULL,
    fecha_inicio        DATE         NULL,
    fecha_objetivo      DATE         NULL,
    origen              VARCHAR(20)  NOT NULL,
    fecha_creacion      DATETIME(6)  NOT NULL,
    fecha_actualizacion DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_proyecto_nombre UNIQUE (nombre),
    KEY idx_proyecto_responsable (responsable_id),
    KEY idx_proyecto_estado (estado),
    CONSTRAINT chk_proyecto_prioridad CHECK (prioridad BETWEEN 1 AND 5),
    CONSTRAINT fk_proyecto_responsable FOREIGN KEY (responsable_id) REFERENCES empleado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tarea (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    titulo                 VARCHAR(200) NOT NULL,
    descripcion            TEXT         NULL,
    responsable_id         BIGINT       NOT NULL,
    solicitante_id         BIGINT       NULL,
    proyecto_id            BIGINT       NULL,
    tarea_padre_id         BIGINT       NULL,
    urgencia_propia        INT          NOT NULL,
    urgencia_calculada     DOUBLE       NOT NULL,
    motivo_urgencia        VARCHAR(500) NULL,
    tareas_bloqueadas      INT          NOT NULL DEFAULT 0,
    personas_bloqueadas    INT          NOT NULL DEFAULT 0,
    bloqueantes_pendientes INT          NOT NULL DEFAULT 0,
    estado                 VARCHAR(20)  NOT NULL,
    origen                 VARCHAR(20)  NOT NULL,
    fecha_creacion         DATETIME(6)  NOT NULL,
    fecha_actualizacion    DATETIME(6)  NOT NULL,
    fecha_limite           DATE         NULL,
    PRIMARY KEY (id),
    KEY idx_tarea_responsable (responsable_id),
    KEY idx_tarea_urgencia_calculada (urgencia_calculada),
    KEY idx_tarea_estado (estado),
    KEY idx_tarea_solicitante (solicitante_id),
    KEY idx_tarea_padre (tarea_padre_id),
    CONSTRAINT chk_tarea_urgencia_propia CHECK (urgencia_propia BETWEEN 1 AND 5),
    CONSTRAINT fk_tarea_responsable FOREIGN KEY (responsable_id) REFERENCES empleado (id),
    CONSTRAINT fk_tarea_solicitante FOREIGN KEY (solicitante_id) REFERENCES empleado (id),
    CONSTRAINT fk_tarea_padre FOREIGN KEY (tarea_padre_id) REFERENCES tarea (id),
    CONSTRAINT fk_tarea_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dependencia (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    tarea_bloqueante_id BIGINT      NOT NULL,
    tarea_bloqueada_id  BIGINT      NOT NULL,
    tipo                VARCHAR(10) NOT NULL,
    fecha_creacion      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dependencia_par UNIQUE (tarea_bloqueante_id, tarea_bloqueada_id),
    KEY idx_dependencia_bloqueada (tarea_bloqueada_id),
    CONSTRAINT fk_dependencia_bloqueante FOREIGN KEY (tarea_bloqueante_id) REFERENCES tarea (id),
    CONSTRAINT fk_dependencia_bloqueada FOREIGN KEY (tarea_bloqueada_id) REFERENCES tarea (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memoria_contexto (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    empleado_id          BIGINT       NOT NULL,
    contenido            TEXT         NOT NULL,
    tipo                 VARCHAR(30)  NOT NULL,
    proyecto_relacionado VARCHAR(200) NULL,
    palabras_clave       VARCHAR(500) NULL,
    activa               BIT(1)       NOT NULL DEFAULT b'1',
    fecha                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memoria_empleado (empleado_id),
    CONSTRAINT fk_memoria_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversacion_chat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    empleado_id BIGINT      NOT NULL,
    mensaje     TEXT        NOT NULL,
    rol         VARCHAR(10) NOT NULL,
    fecha_hora  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_empleado_fecha (empleado_id, fecha_hora),
    CONSTRAINT fk_chat_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS proyecto_miembro (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    proyecto_id  BIGINT       NOT NULL,
    empleado_id  BIGINT       NOT NULL,
    rol_proyecto VARCHAR(120) NULL,
    dedicacion   INT          NOT NULL,
    fecha_alta   DATE         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_proyecto_miembro UNIQUE (proyecto_id, empleado_id),
    KEY idx_miembro_empleado (empleado_id),
    CONSTRAINT fk_miembro_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto (id),
    CONSTRAINT fk_miembro_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hito (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    proyecto_id      BIGINT       NOT NULL,
    nombre           VARCHAR(200) NOT NULL,
    descripcion      VARCHAR(500) NULL,
    fecha_objetivo   DATE         NULL,
    completado       BIT(1)       NOT NULL DEFAULT b'0',
    fecha_completado DATE         NULL,
    PRIMARY KEY (id),
    KEY idx_hito_proyecto (proyecto_id),
    CONSTRAINT fk_hito_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS proyecto_nota (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    proyecto_id BIGINT      NOT NULL,
    autor_id    BIGINT      NULL,
    tipo        VARCHAR(20) NOT NULL,
    contenido   TEXT        NOT NULL,
    abierta     BIT(1)      NOT NULL DEFAULT b'0',
    fecha       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_nota_proyecto_fecha (proyecto_id, fecha),
    CONSTRAINT fk_nota_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto (id),
    CONSTRAINT fk_nota_autor FOREIGN KEY (autor_id) REFERENCES empleado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- Migraciones (bases de datos ya existentes)

-- v2: gestión de proyectos. Las tareas pasan a poder pertenecer a un proyecto.
ALTER TABLE tarea ADD COLUMN IF NOT EXISTS proyecto_id BIGINT NULL;
ALTER TABLE tarea ADD FOREIGN KEY IF NOT EXISTS fk_tarea_proyecto (proyecto_id) REFERENCES proyecto (id);
