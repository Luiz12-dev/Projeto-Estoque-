CREATE TABLE peca_cortada (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    largura_chapa_mm DECIMAL(10, 2) NOT NULL,
    comprimento_chapa_mm DECIMAL(10, 2) NOT NULL,
    valor_chapa DECIMAL(19, 2) NOT NULL,
    largura_peca_mm DECIMAL(10, 2) NOT NULL,
    comprimento_peca_mm DECIMAL(10, 2) NOT NULL,
    quantidade INTEGER NOT NULL DEFAULT 1,
    valor_unitario_calculado DECIMAL(19, 2) NOT NULL,
    produto_id BIGINT NOT NULL REFERENCES produto(id),
    ordem_servico_id BIGINT NOT NULL REFERENCES ordem_servico(id),
    movimentacao_id BIGINT REFERENCES movimentacao(id),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_peca_cortada_ordem_servico_id ON peca_cortada(ordem_servico_id);
