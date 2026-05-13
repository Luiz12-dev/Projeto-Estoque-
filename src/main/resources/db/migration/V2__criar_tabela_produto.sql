CREATE TABLE produto (
    id                 BIGSERIAL      PRIMARY KEY,
    nome               VARCHAR(255)   NOT NULL UNIQUE,
    categoria          VARCHAR(100),
    quantidade_atual   NUMERIC(19,4)  NOT NULL DEFAULT 0,
    quantidade_minima  NUMERIC(19,4)  NOT NULL DEFAULT 0,
    unidade_medida     VARCHAR(10)    NOT NULL,
    criado_em          TIMESTAMP      NOT NULL,
    atualizado_em      TIMESTAMP      NOT NULL
);
