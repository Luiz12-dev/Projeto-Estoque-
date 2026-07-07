-- Corrige race condition na geração de código de OS.
-- Usa sequence do PostgreSQL para garantir unicidade em concorrência.
CREATE SEQUENCE IF NOT EXISTS os_codigo_seq START WITH 1 INCREMENT BY 1;

-- 3. Atualiza o valor atual da sequence para o maior código existente ou 1
SELECT setval('os_codigo_seq', COALESCE((SELECT MAX(CAST(SUBSTRING(codigo FROM 4) AS BIGINT)) FROM ordem_servico), 1));
