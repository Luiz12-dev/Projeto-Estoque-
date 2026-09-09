-- Categoria deixa de ser texto digitado dentro do produto e passa a existir por
-- conta propria.
--
-- Duas razoes. A primeira e do usuario: ele precisa criar uma categoria vazia e
-- so depois cadastrar itens dentro dela; enquanto categoria fosse um campo do
-- produto, ela so nascia junto com o primeiro produto. A segunda e que texto
-- livre se degrada sozinho -- o banco de ensaio ja tinha "disco" e
-- "Discos de corte" como coisas diferentes.

CREATE TABLE categoria (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(80) NOT NULL
);

-- Sem acento e sem caixa: "Chapas" e "chapas" passam a ser a mesma categoria, e
-- o proximo cadastro esbarra na restricao em vez de criar uma gemea.
CREATE UNIQUE INDEX ux_categoria_nome ON categoria (LOWER(nome));

-- Traz o que ja existe. DISTINCT sobre o nome aparado, ignorando vazio; entre
-- variacoes de caixa o MIN escolhe uma, e os produtos das outras apontam para
-- ela na atualizacao abaixo.
INSERT INTO categoria (nome)
SELECT MIN(TRIM(categoria))
FROM produto
WHERE categoria IS NOT NULL AND TRIM(categoria) <> ''
GROUP BY LOWER(TRIM(categoria));

ALTER TABLE produto ADD COLUMN categoria_id BIGINT;

UPDATE produto p
SET categoria_id = c.id
FROM categoria c
WHERE LOWER(TRIM(p.categoria)) = LOWER(c.nome);

ALTER TABLE produto
    ADD CONSTRAINT fk_produto_categoria
    FOREIGN KEY (categoria_id) REFERENCES categoria (id);

-- Produto sem categoria continua valido: categoria_id fica nulo, e a tela junta
-- esses itens num grupo "Sem categoria". Apagar uma categoria com itens dentro
-- e barrado pela FK, que e o comportamento certo -- o usuario move os itens
-- antes.

ALTER TABLE produto DROP COLUMN categoria;
