-- Adiciona coluna de versionamento para optimistic locking no Produto.
-- Previne race conditions no controle de estoque (lost updates).
ALTER TABLE produto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
