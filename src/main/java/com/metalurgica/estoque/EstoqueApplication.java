package com.metalurgica.estoque;

import com.metalurgica.estoque.config.VerificadorDeConfiguracao;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class EstoqueApplication {

	public static void main(String[] args) {
		SpringApplication aplicacao = new SpringApplication(EstoqueApplication.class);
		// Registrado como listener, e não como bean, para rodar antes de qualquer
		// bean existir: um erro de configuração precisa aparecer limpo, e não
		// enterrado sob a pilha do Hibernate tentando conectar no banco.
		aplicacao.addListeners(new VerificadorDeConfiguracao());
		aplicacao.run(args);
	}

}
