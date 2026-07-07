package com.metalurgica.estoque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EstoqueApplicationTests {

	@Test
	@DisplayName("Deve carregar o contexto Spring completo sem erros")
	void contextLoads() {
		// Arrange / Act / Assert — implícito: se o contexto subiu, o teste passou
	}
}
