CREATE TABLE chamado (
    id BIGINT NOT NULL AUTO_INCREMENT,
    titulo VARCHAR(200) NOT NULL,
    descricao TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    prioridade VARCHAR(10) NOT NULL,
    solicitante_id BIGINT NOT NULL,
    atendente_id BIGINT NULL,
    categoria_id BIGINT NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    resolvido_em DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_chamado_status CHECK (
        status IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO')
    ),
    CONSTRAINT ck_chamado_prioridade CHECK (
        prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')
    ),
    CONSTRAINT fk_chamado_solicitante FOREIGN KEY (solicitante_id)
        REFERENCES usuario (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chamado_atendente FOREIGN KEY (atendente_id)
        REFERENCES usuario (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chamado_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_chamado_solicitante_criado (solicitante_id, criado_em),
    INDEX idx_chamado_atendente_status (atendente_id, status),
    INDEX idx_chamado_status_prioridade (status, prioridade),
    INDEX idx_chamado_categoria (categoria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
