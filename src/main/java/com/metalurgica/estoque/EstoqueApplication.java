package com.metalurgica.estoque;

import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.Role;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class EstoqueApplication {

	public static void main(String[] args) {
		SpringApplication.run(EstoqueApplication.class, args);
	}

	/**
	 * Seed de usuário padrão — APENAS ativo em perfil "dev" ou "default".
	 * Não executa em produção. (fix 1.4, 2.5)
	 */
	@Bean
	@Profile({ "dev", "default" })
	CommandLineRunner seedUsuario(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			try {
				if (usuarioRepository.count() == 0) {
					Usuario usuario = Usuario.builder()
							.nome("Cadu")
							.login("cadu")
							.senha(passwordEncoder.encode("123"))
							.role(Role.ADMIN)
							.build();

					usuarioRepository.save(usuario);
					log.info(">>> Usuário seed criado: login='cadu', senha='123', role=ADMIN");
				} else {
					log.info(">>> Usuário seed já existe. Pulando criação.");
				}
			} catch (DataIntegrityViolationException e) {
				log.warn(">>> Seed de usuário já existe (race condition em startup paralelo). Ignorando.");
			}
		};
	}
}
