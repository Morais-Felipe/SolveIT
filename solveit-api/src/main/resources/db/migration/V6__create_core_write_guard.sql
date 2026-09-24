CREATE TABLE core_write_guard (
    id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_core_write_guard_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO core_write_guard (id) VALUES (1);
