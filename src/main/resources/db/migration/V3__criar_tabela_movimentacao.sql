CREATE TABLE movimentacao (
    id              BIGSERIAL      PRIMARY KEY,
    tipo            VARCHAR(10)    NOT NULL,
    quantidade      NUMERIC(19,4)  NOT NULL,
    valor_unitario  NUMERIC(19,2),
    data_hora       TIMESTAMP      NOT NULL,
    observacao      TEXT,
    produto_id      BIGINT         NOT NULL REFERENCES produto(id),
    usuario_id      BIGINT         NOT NULL REFERENCES usuario(id)
);

CREATE INDEX idx_movimentacao_produto ON movimentacao(produto_id);
CREATE INDEX idx_movimentacao_data    ON movimentacao(data_hora);
