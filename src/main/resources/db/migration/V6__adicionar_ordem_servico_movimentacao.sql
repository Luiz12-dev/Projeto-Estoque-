ALTER TABLE movimentacao ADD COLUMN ordem_servico_id BIGINT REFERENCES ordem_servico(id);
