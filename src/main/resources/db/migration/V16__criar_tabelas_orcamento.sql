-- Orcamento de corte: o que se cobra do cliente, distinto do registro de um
-- corte realizado (peca_cortada), que lanca apenas o material no estoque.
--
-- Um orcamento pertence a uma empresa e tem varios itens, porque o cliente
-- raramente pede uma peca so.

CREATE TABLE orcamento (
    id                 BIGSERIAL PRIMARY KEY,
    codigo             VARCHAR(20)    NOT NULL UNIQUE,
    empresa_id         BIGINT         NOT NULL REFERENCES empresa(id),
    situacao           VARCHAR(20)    NOT NULL DEFAULT 'PENDENTE',
    margem_percentual  DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    valor_total        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    observacao         TEXT,
    usuario_id         BIGINT         NOT NULL REFERENCES usuario(id),
    criado_em          TIMESTAMP      NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orcamento_empresa_id ON orcamento (empresa_id);
CREATE INDEX idx_orcamento_situacao   ON orcamento (situacao);
CREATE INDEX idx_orcamento_criado_em  ON orcamento (criado_em);

-- Sequence propria para o codigo, no mesmo padrao da OS (ver V7): garante
-- unicidade sob concorrencia, sem depender de MAX(codigo) + 1.
CREATE SEQUENCE IF NOT EXISTS orcamento_codigo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orcamento_item (
    id                       BIGSERIAL PRIMARY KEY,
    orcamento_id             BIGINT         NOT NULL REFERENCES orcamento(id) ON DELETE CASCADE,
    nome                     VARCHAR(150)   NOT NULL,
    produto_id               BIGINT         NOT NULL REFERENCES produto(id),

    largura_peca_mm          DECIMAL(10, 2) NOT NULL,
    comprimento_peca_mm      DECIMAL(10, 2) NOT NULL,
    quantidade               INTEGER        NOT NULL DEFAULT 1,

    -- Corte interno: zero para quem so corta retangulo
    quantidade_furos         INTEGER        NOT NULL DEFAULT 0,
    diametro_furo_mm         DECIMAL(10, 2) NOT NULL DEFAULT 0,
    corte_extra_metros       DECIMAL(10, 3) NOT NULL DEFAULT 0,

    -- Copia congelada dos parametros da chapa no momento do orcamento.
    -- Um orcamento e um compromisso com o cliente: se a chapa for reajustada
    -- no mes seguinte, o valor ja informado nao pode mudar sozinho.
    largura_chapa_mm         DECIMAL(10, 2) NOT NULL,
    comprimento_chapa_mm     DECIMAL(10, 2) NOT NULL,
    valor_chapa              DECIMAL(19, 2) NOT NULL,
    preco_metro_corte        DECIMAL(19, 2) NOT NULL,

    comprimento_corte_metros DECIMAL(10, 4) NOT NULL,
    custo_material_unitario  DECIMAL(19, 2) NOT NULL,
    custo_corte_unitario     DECIMAL(19, 2) NOT NULL,
    preco_unitario           DECIMAL(19, 2) NOT NULL,
    preco_total              DECIMAL(19, 2) NOT NULL
);

CREATE INDEX idx_orcamento_item_orcamento_id ON orcamento_item (orcamento_id);
