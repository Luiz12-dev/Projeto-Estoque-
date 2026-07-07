-- Adiciona coluna de versionamento para optimistic locking na OrdemServico.
-- Previne lost updates quando dois usuários editam a mesma OS simultaneamente.
ALTER TABLE ordem_servico ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
