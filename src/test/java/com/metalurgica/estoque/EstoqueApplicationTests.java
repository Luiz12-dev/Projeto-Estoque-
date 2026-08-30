package com.metalurgica.estoque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EstoqueApplicationTests extends TesteDeIntegracao {

	@Test
	@DisplayName("O contexto sobe contra PostgreSQL real, com as migrations aplicadas")
	void contextLoads() {
		// Se o contexto subiu, entao: as 16 migrations rodaram na ordem, o
		// ddl-auto=validate conferiu as entidades contra o esquema resultante, e
		// o bootstrap de administrador encontrou caminho valido. E o teste que
		// mais se aproxima de provar que a aplicacao sobe em producao.
	}
}
