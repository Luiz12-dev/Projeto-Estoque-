package com.metalurgica.estoque;

import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@SpringBootApplication
public class EstoqueApplication {

	public static void main(String[] args) {
		SpringApplication.run(EstoqueApplication.class, args);
	}

	@Bean
	CommandLineRunner seedUsuario(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (usuarioRepository.count() == 0) {
				Usuario usuario = Usuario.builder()
						.nome("Cadu")
						.login("cadu")
						.senha(passwordEncoder.encode("123"))
						.build();

				usuarioRepository.save(usuario);
				log.info(">>> Usuário seed criado: login='cadu', senha='123'");
			} else {
				log.info(">>> Usuário seed já existe. Pulando criação.");
			}
		};
	}
}
