-- A abertura de um chamado não tem estado anterior. Em vez de inventar um, o
-- histórico passa a aceitar status_anterior nulo somente quando status_novo é ABERTO.
-- Históricos já existentes não são alterados nem recebem registros retroativos.
ALTER TABLE historico_status
    MODIFY COLUMN status_anterior VARCHAR(20) NULL;

ALTER TABLE historico_status
    ADD CONSTRAINT ck_historico_anterior_somente_abertura CHECK (
        status_anterior IS NOT NULL OR status_novo = 'ABERTO'
    );
