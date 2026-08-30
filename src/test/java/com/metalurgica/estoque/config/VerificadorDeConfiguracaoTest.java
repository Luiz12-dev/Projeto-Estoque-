package com.metalurgica.estoque.config;

import com.metalurgica.estoque.config.VerificadorDeConfiguracao.ConfiguracaoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O que estes testes protegem: antes desta verificação, o
 * application.properties trazia um segredo embutido e versionado no Git, e um
 * comentário afirmando que a aplicação falhava sem a variável. Ela não falhava.
 * Qualquer pessoa com o repositório forjava token de administrador.
 */
class VerificadorDeConfiguracaoTest {

    private static final String SEGREDO_VALIDO = "a".repeat(VerificadorDeConfiguracao.TAMANHO_MINIMO_DO_SEGREDO);

    @Nested
    @DisplayName("Segredo ausente")
    class SegredoAusente {

        @ParameterizedTest(name = "recusa segredo [{0}]")
        @NullSource
        @ValueSource(strings = { "", " ", "   \t  " })
        @DisplayName("Não sobe sem JWT_SECRET definido")
        void naoSobeSemSegredo(String segredo) {
            assertThatThrownBy(() -> VerificadorDeConfiguracao.validarSegredoJwt(segredo))
                    .isInstanceOf(ConfiguracaoInvalidaException.class)
                    .hasMessageContaining("JWT_SECRET");
        }

        @Test
        @DisplayName("A mensagem ensina como gerar a chave, não apenas que ela falta")
        void mensagemTrazOComandoPronto() {
            // Erro de configuração que só diz "está errado" faz quem implanta
            // escolher um valor fraco por pressa.
            assertThatThrownBy(() -> VerificadorDeConfiguracao.validarSegredoJwt(null))
                    .hasMessageContaining("openssl rand")
                    .hasMessageContaining("PowerShell")
                    .hasMessageContaining(".env.example");
        }
    }

    @Nested
    @DisplayName("Segredo fraco")
    class SegredoFraco {

        @Test
        @DisplayName("Recusa chave curta demais para HMAC256")
        void recusaChaveCurta() {
            assertThatThrownBy(() -> VerificadorDeConfiguracao.validarSegredoJwt("chave-curta"))
                    .isInstanceOf(ConfiguracaoInvalidaException.class)
                    .hasMessageContaining("11 caracteres")
                    .hasMessageContaining("mínimo é 32");
        }

        @Test
        @DisplayName("Espaço em volta não conta como tamanho de chave")
        void espacoNaoContaComoTamanho() {
            String curtaComEspacos = "   " + "a".repeat(10) + "   ";
            assertThatThrownBy(() -> VerificadorDeConfiguracao.validarSegredoJwt(curtaComEspacos))
                    .isInstanceOf(ConfiguracaoInvalidaException.class);
        }

        @Test
        @DisplayName("Aceita exatamente o tamanho mínimo")
        void aceitaOTamanhoMinimo() {
            assertThat(SEGREDO_VALIDO).hasSize(32);
            assertThatCode(() -> VerificadorDeConfiguracao.validarSegredoJwt(SEGREDO_VALIDO))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Segredo forte passa sem reclamar")
    void segredoForteEhAceito() {
        String segredo = "3f9c1a7e0b5d84623fae91c7d0b45e8a6f2c93d17b0e54a8c6d92f13b7e0a54c";
        assertThatCode(() -> VerificadorDeConfiguracao.validarSegredoJwt(segredo))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("O verificador roda de fato quando o ambiente e preparado")
    void verificadorRodaNoEventoDoAmbiente() {
        // Garante que a validação está ligada ao ciclo de vida, e não apenas
        // disponível como método estático que ninguém chama.
        MockEnvironment ambienteSemSegredo = new MockEnvironment();
        assertThatThrownBy(() -> dispararEvento(ambienteSemSegredo))
                .isInstanceOf(ConfiguracaoInvalidaException.class);

        MockEnvironment ambienteOk = new MockEnvironment()
                .withProperty(VerificadorDeConfiguracao.PROPRIEDADE_SEGREDO, SEGREDO_VALIDO);
        assertThatCode(() -> dispararEvento(ambienteOk)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Roda depois do carregamento do application.properties, nao antes")
    void rodaDepoisDoCarregamentoDasPropriedades() {
        // Regressao de um bug real: com HIGHEST_PRECEDENCE o verificador rodava
        // antes do listener que carrega o application.properties, lia a
        // propriedade como nula e recusava subir mesmo com JWT_SECRET definida.
        // A fase inteira ainda ocorre antes de qualquer bean, entao continua
        // falhando antes de tentar conectar no banco.
        int ordemDoCarregadorDePropriedades =
                EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER;

        assertThat(new VerificadorDeConfiguracao().getOrder())
                .isGreaterThan(ordemDoCarregadorDePropriedades);
    }

    private static void dispararEvento(ConfigurableEnvironment ambiente) {
        new VerificadorDeConfiguracao().onApplicationEvent(
                new ApplicationEnvironmentPreparedEvent(
                        new DefaultBootstrapContext(),
                        new SpringApplication(),
                        new String[0],
                        ambiente));
    }
}
