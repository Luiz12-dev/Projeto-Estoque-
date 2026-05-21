CREATE TABLE ordem_servico (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    descricao VARCHAR(255) NOT NULL,
    cliente VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    prioridade VARCHAR(10) NOT NULL DEFAULT 'MEDIA',
    data_abertura TIMESTAMP NOT NULL DEFAULT NOW(),
    data_conclusao TIMESTAMP,
    observacao TEXT,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
