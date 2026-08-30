package com.metalurgica.estoque.config;

import com.metalurgica.estoque.config.VerificadorDeConfiguracao.ConfiguracaoInvalidaException;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.Role;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que estes testes protegem: antes disto, o seed de usuário rodava apenas nos
 * perfis de desenvolvimento. Em produção o sistema subia sem nenhum usuário,
 * ninguém conseguia autenticar, e como criar usuário exige ADMIN, o sistema
 * ficava trancado por fora sem nenhuma mensagem explicando o porquê.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdministradorTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    /** Encoder real: interessa provar que a senha é gravada com hash, não em claro. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MockEnvironment ambiente;

    @BeforeEach
    void setUp() {
        ambiente = new MockEnvironment();
    }

    private BootstrapAdministrador comCredenciais(String login, String senha, String nome) {
        return new BootstrapAdministrador(usuarioRepository, passwordEncoder, ambiente, login, senha, nome);
    }

    private BootstrapAdministrador semCredenciais() {
        return comCredenciais("", "", "");
    }

    private Usuario capturarSalvo() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Banco que já tem usuário")
    class BancoComUsuario {

        @Test
        @DisplayName("Não cria nada e não reclama — é o restart normal")
        void naoFazNada() {
            when(usuarioRepository.count()).thenReturn(1L);

            assertThatCode(() -> semCredenciais().run(null)).doesNotThrowAnyException();

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ignora as variáveis de ambiente depois do primeiro administrador")
        void ignoraVariaveisDepoisDoPrimeiro() {
            // Sem isto, um restart com as variáveis ainda definidas tentaria
            // recriar o administrador e falharia por login duplicado.
            when(usuarioRepository.count()).thenReturn(3L);
            ambiente.setActiveProfiles("prod");

            assertThatCode(() -> comCredenciais("chefe", "senhaForte", "Chefe").run(null))
                    .doesNotThrowAnyException();

            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Banco vazio com variáveis definidas")
    class BancoVazioComVariaveis {

        @BeforeEach
        void bancoVazio() {
            when(usuarioRepository.count()).thenReturn(0L);
        }

        @Test
        @DisplayName("Cria o administrador informado, como ADMIN")
        void criaOAdministradorInformado() {
            ambiente.setActiveProfiles("prod");

            comCredenciais("leo.fantineli", "SenhaBemForte123", "Leo Fantineli").run(null);

            Usuario salvo = capturarSalvo();
            assertThat(salvo.getLogin()).isEqualTo("leo.fantineli");
            assertThat(salvo.getNome()).isEqualTo("Leo Fantineli");
            assertThat(salvo.getRole()).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("A senha é gravada com hash, nunca em texto claro")
        void senhaEhGravadaComHash() {
            ambiente.setActiveProfiles("prod");
            String senhaEmClaro = "SenhaBemForte123";

            comCredenciais("leo", senhaEmClaro, "Leo").run(null);

            Usuario salvo = capturarSalvo();
            assertThat(salvo.getSenha()).isNotEqualTo(senhaEmClaro);
            assertThat(salvo.getSenha()).startsWith("$2");
            assertThat(passwordEncoder.matches(senhaEmClaro, salvo.getSenha())).isTrue();
        }

        @Test
        @DisplayName("Sem ADMIN_NOME, usa o login como nome")
        void usaLoginComoNomeQuandoNaoInformado() {
            ambiente.setActiveProfiles("prod");

            comCredenciais("leo.fantineli", "SenhaBemForte123", "").run(null);

            assertThat(capturarSalvo().getNome()).isEqualTo("leo.fantineli");
        }

        @Test
        @DisplayName("Espaços em volta do login são descartados")
        void descartaEspacosNoLogin() {
            ambiente.setActiveProfiles("prod");

            comCredenciais("  leo  ", "SenhaBemForte123", "  Leo  ").run(null);

            Usuario salvo = capturarSalvo();
            assertThat(salvo.getLogin()).isEqualTo("leo");
            assertThat(salvo.getNome()).isEqualTo("Leo");
        }

        @Test
        @DisplayName("As variáveis vencem o atalho de desenvolvimento")
        void variaveisVencemOAtalhoDeDesenvolvimento() {
            // Perfil de desenvolvimento, mas com credenciais explícitas: quem
            // configurou quis aquele usuário, não o cadu/123.
            comCredenciais("outro.admin", "SenhaBemForte123", "Outro").run(null);

            assertThat(capturarSalvo().getLogin()).isEqualTo("outro.admin");
        }

        @Test
        @DisplayName("Se outra instância criou primeiro, segue sem quebrar")
        void toleraCorridaEntreInstancias() {
            ambiente.setActiveProfiles("prod");
            when(usuarioRepository.save(any())).thenThrow(new DataIntegrityViolationException("login duplicado"));

            assertThatCode(() -> comCredenciais("leo", "SenhaBemForte123", "Leo").run(null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Banco vazio sem variáveis")
    class BancoVazioSemVariaveis {

        @BeforeEach
        void bancoVazio() {
            when(usuarioRepository.count()).thenReturn(0L);
        }

        @Test
        @DisplayName("Em desenvolvimento, cria o cadu/123 de conveniência")
        void criaOAtalhoEmDesenvolvimento() {
            // Sem perfil ativo o Spring roda em "default", que conta como dev.
            semCredenciais().run(null);

            Usuario salvo = capturarSalvo();
            assertThat(salvo.getLogin()).isEqualTo("cadu");
            assertThat(passwordEncoder.matches("123", salvo.getSenha())).isTrue();
            assertThat(salvo.getRole()).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("No perfil dev também")
        void criaOAtalhoNoPerfilDev() {
            ambiente.setActiveProfiles("dev");

            semCredenciais().run(null);

            assertThat(capturarSalvo().getLogin()).isEqualTo("cadu");
        }

        @Test
        @DisplayName("Fora de desenvolvimento, RECUSA subir em vez de trancar o sistema por fora")
        void recusaSubirEmProducao() {
            ambiente.setActiveProfiles("prod");

            assertThatThrownBy(() -> semCredenciais().run(null))
                    .isInstanceOf(ConfiguracaoInvalidaException.class)
                    .hasMessageContaining("ADMIN_LOGIN")
                    .hasMessageContaining("ADMIN_SENHA");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Senha sem login não vale como configuração")
        void senhaSemLoginNaoBasta() {
            ambiente.setActiveProfiles("prod");

            assertThatThrownBy(() -> comCredenciais("", "SenhaBemForte123", "").run(null))
                    .isInstanceOf(ConfiguracaoInvalidaException.class);
        }

        @Test
        @DisplayName("Login sem senha não vale como configuração")
        void loginSemSenhaNaoBasta() {
            ambiente.setActiveProfiles("prod");

            assertThatThrownBy(() -> comCredenciais("leo", "", "").run(null))
                    .isInstanceOf(ConfiguracaoInvalidaException.class);
        }

        @Test
        @DisplayName("A mensagem diz o que configurar e onde ver o exemplo")
        void mensagemEhAcionavel() {
            ambiente.setActiveProfiles("prod");

            assertThatThrownBy(() -> semCredenciais().run(null))
                    .hasMessageContaining(".env.example")
                    .hasMessageContaining("tela de Usuarios");
        }
    }
}
