-- A primeira OS de uma instalacao nova saia como OS-0002.
--
-- A V7 posicionou o contador com setval('os_codigo_seq', COALESCE(MAX(...), 1)).
-- Sem o terceiro argumento, o setval marca o valor como JA USADO. Numa tabela
-- com OS isso esta certo: o contador fica no maior codigo e a proxima e' ele
-- mais um. Numa tabela VAZIA, o COALESCE devolve 1, o 1 fica marcado como
-- usado, e o primeiro nextval devolve 2.
--
-- Nunca apareceu porque todo banco testado ate aqui ja tinha OS. Apareceu na
-- simulacao do primeiro dia de uso, com o banco recem-criado -- que e'
-- exatamente o estado da maquina do cliente na instalacao.
--
-- So age quando nao existe nenhuma OS. Com OS cadastradas o contador ja esta
-- certo, e reposiciona-lo arriscaria devolver o codigo de uma OS apagada.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ordem_servico) THEN
        PERFORM setval('os_codigo_seq', 1, false);
    END IF;
END $$;
