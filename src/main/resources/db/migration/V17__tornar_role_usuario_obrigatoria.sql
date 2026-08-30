-- A entidade Usuario declara @Column(nullable = false) na role desde a V11, mas
-- a V11 criou a coluna apenas com DEFAULT, sem NOT NULL. O banco aceitava um
-- usuario sem perfil enquanto o codigo garantia que isso nao podia acontecer.
--
-- O ddl-auto=validate do Hibernate nao pega esse tipo de divergencia: ele
-- confere existencia de tabela e coluna e o tipo, nao a nulidade. A diferenca
-- so apareceu na comparacao manual do esquema real contra as entidades.
--
-- Um usuario com role nula quebraria o carregamento da entidade no login.
-- Nenhum caminho da aplicacao grava nulo hoje, entao o UPDATE abaixo tende a
-- nao afetar nenhuma linha; ele existe para o SET NOT NULL nao falhar em bases
-- que tenham sido manipuladas direto no banco.

UPDATE usuario SET role = 'OPERADOR' WHERE role IS NULL;

ALTER TABLE usuario ALTER COLUMN role SET NOT NULL;
