package com.metalurgica.estoque.config;

import com.metalurgica.estoque.config.VerificadorDeConfiguracao.ConfiguracaoInvalidaException;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.Role;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Garante que existe pelo menos um administrador capaz de entrar no sistema.
 * <p>
 * Substitui o antigo seed restrito aos perfis de desenvolvimento. Aquele
 * arranjo deixava produção sem saída: o sistema subia, ninguém autenticava, e
 * como {@code /api/usuarios} exige ADMIN, não havia caminho para criar o
 * primeiro usuário — travado por fora.
 * <p>
 * A regra é uma só, em quatro situações:
 * <ol>
 * <li>Já existe usuário: não faz nada e não reclama. É o restart do dia a dia.</li>
 * <li>Vazio, com as variáveis de ambiente: cria o administrador informado.</li>
 * <li>Vazio, em desenvolvimento, sem variáveis: cria {@code cadu/123} e avisa
 * alto no log.</li>
 * <li>Vazio, fora de desenvolvimento, sem variáveis: recusa subir, dizendo o
 * que configurar.</li>
 * </ol>
 * Manter isso numa classe só, e não espalhado entre um seed de desenvolvimento
 * e um de produção, evita o cenário em que os dois rodam e brigam pelo mesmo
 * registro.
 */
@Slf4j
@Component
public class BootstrapAdministrador implements ApplicationRunner {

    /** Perfis em que a conveniência de um login pronto vale mais que o rigor. */
    private static final List<String> PERFIS_DE_DESENVOLVIMENTO = List.of("dev", "test");

    private static final String LOGIN_PADRAO_DEV = "cadu";
    private static final String SENHA_PADRAO_DEV = "123";
    private static final String NOME_PADRAO_DEV = "Cadu";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment ambiente;
    private final String loginConfigurado;
    private final String senhaConfigurada;
    private final String nomeConfigurado;

    public BootstrapAdministrador(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            Environment ambiente,
            @Value("${ADMIN_LOGIN:}") String loginConfigurado,
            @Value("${ADMIN_SENHA:}") String senhaConfigurada,
            @Value("${ADMIN_NOME:}") String nomeConfigurado) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.ambiente = ambiente;
        this.loginConfigurado = loginConfigurado;
        this.senhaConfigurada = senhaConfigurada;
        this.nomeConfigurado = nomeConfigurado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            log.debug("Já existe usuário cadastrado. Bootstrap de administrador não é necessário.");
            return;
        }

        if (temCredenciaisConfiguradas()) {
            criar(loginConfigurado.strip(), senhaConfigurada, nomeOuPadrao());
            log.info(">>> Administrador inicial '{}' criado a partir das variáveis de ambiente.",
                    loginConfigurado.strip());
            return;
        }

        if (ehDesenvolvimento()) {
            criar(LOGIN_PADRAO_DEV, SENHA_PADRAO_DEV, NOME_PADRAO_DEV);
            log.warn(">>> ATENÇÃO: administrador de desenvolvimento criado — login '{}', senha '{}'. "
                    + "Nunca use este perfil em produção.", LOGIN_PADRAO_DEV, SENHA_PADRAO_DEV);
            return;
        }

        throw new ConfiguracaoInvalidaException(mensagemDeFalta());
    }

    private boolean temCredenciaisConfiguradas() {
        return !loginConfigurado.isBlank() && !senhaConfigurada.isBlank();
    }

    private String nomeOuPadrao() {
        return nomeConfigurado.isBlank() ? loginConfigurado.strip() : nomeConfigurado.strip();
    }

    /** Sem perfil ativo o Spring roda em "default", que aqui conta como desenvolvimento. */
    private boolean ehDesenvolvimento() {
        String[] ativos = ambiente.getActiveProfiles();
        return ativos.length == 0
                || Arrays.stream(ativos).anyMatch(PERFIS_DE_DESENVOLVIMENTO::contains);
    }

    private void criar(String login, String senha, String nome) {
        try {
            usuarioRepository.save(Usuario.builder()
                    .nome(nome)
                    .login(login)
                    .senha(passwordEncoder.encode(senha))
                    .role(Role.ADMIN)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Duas instâncias subindo ao mesmo tempo contra o mesmo banco vazio.
            // A outra ganhou; o objetivo (existir um administrador) foi atingido.
            log.warn(">>> Administrador já havia sido criado por outra instância. Seguindo.");
        }
    }

    private String mensagemDeFalta() {
        return """

                ===============================================================
                 A APLICACAO NAO PODE SUBIR: nenhum administrador cadastrado
                ===============================================================

                 O banco de dados nao tem nenhum usuario, e as variaveis para
                 criar o primeiro administrador nao foram definidas.

                 Sem isto ninguem consegue entrar, e como so um ADMIN cria
                 usuario, o sistema ficaria trancado por fora.

                 Defina antes de subir:

                   ADMIN_LOGIN=nome.do.responsavel
                   ADMIN_SENHA=<uma senha forte, trocada no primeiro acesso>
                   ADMIN_NOME=Nome Completo         (opcional)

                 Depois que o primeiro administrador existir, estas variaveis
                 sao ignoradas e podem ser removidas. Os demais logins da
                 oficina sao criados pela tela de Usuarios.

                 Veja .env.example na raiz do projeto.

                ===============================================================
                """;
    }
}
