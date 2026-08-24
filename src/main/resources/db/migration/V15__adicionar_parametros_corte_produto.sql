-- Parametros de corte da chapa.
-- Permitem que o orcamento de corte exija apenas as medidas da peca:
-- as dimensoes e o preco por metro passam a vir do cadastro do produto,
-- em vez de serem redigitados a cada corte.
--
-- Sao opcionais: so fazem sentido para chapa. Parafuso e tinta nao tem
-- perimetro. O modulo de Cortes so oferece produtos com esses campos
-- preenchidos (junto de valor_unitario).
ALTER TABLE produto ADD COLUMN largura_mm DECIMAL(10, 2);
ALTER TABLE produto ADD COLUMN comprimento_mm DECIMAL(10, 2);
ALTER TABLE produto ADD COLUMN preco_metro_corte DECIMAL(19, 2);
