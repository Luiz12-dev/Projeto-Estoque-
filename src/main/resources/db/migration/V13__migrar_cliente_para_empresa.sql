-- Cria uma Empresa para cada valor distinto já usado no campo livre ordem_servico.cliente
INSERT INTO empresa (nome, criado_em, atualizado_em)
SELECT DISTINCT TRIM(cliente), NOW(), NOW()
FROM ordem_servico
WHERE cliente IS NOT NULL AND TRIM(cliente) <> '';

ALTER TABLE ordem_servico ADD COLUMN empresa_id BIGINT;

UPDATE ordem_servico os
SET empresa_id = e.id
FROM empresa e
WHERE e.nome = TRIM(os.cliente);

ALTER TABLE ordem_servico ALTER COLUMN empresa_id SET NOT NULL;
ALTER TABLE ordem_servico ADD CONSTRAINT fk_os_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id);
CREATE INDEX idx_os_empresa_id ON ordem_servico (empresa_id);

ALTER TABLE ordem_servico DROP COLUMN cliente;
