CREATE TABLE historico_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chamado_id BIGINT NOT NULL,
    status_anterior VARCHAR(20) NOT NULL,
    status_novo VARCHAR(20) NOT NULL,
    alterado_por_id BIGINT NOT NULL,
    alterado_em DATETIME(6) NOT NULL,
    observacao VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_historico_status_anterior CHECK (
        status_anterior IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO')
    ),
    CONSTRAINT ck_historico_status_novo CHECK (
        status_novo IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO')
    ),
    CONSTRAINT fk_historico_chamado FOREIGN KEY (chamado_id)
        REFERENCES chamado (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historico_alterado_por FOREIGN KEY (alterado_por_id)
        REFERENCES usuario (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_historico_chamado_alterado (chamado_id, alterado_em),
    INDEX idx_historico_alterado_por (alterado_por_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
