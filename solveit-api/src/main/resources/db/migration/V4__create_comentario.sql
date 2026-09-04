CREATE TABLE comentario (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chamado_id BIGINT NOT NULL,
    autor_id BIGINT NOT NULL,
    texto TEXT NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comentario_chamado FOREIGN KEY (chamado_id)
        REFERENCES chamado (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comentario_autor FOREIGN KEY (autor_id)
        REFERENCES usuario (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_comentario_chamado_criado (chamado_id, criado_em),
    INDEX idx_comentario_autor (autor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
