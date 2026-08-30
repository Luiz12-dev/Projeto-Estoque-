package com.metalurgica.estoque.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Confere, na subida, que o ambiente está configurado para rodar com segurança.
 * <p>
 * Roda no {@link ApplicationEnvironmentPreparedEvent}: o momento em que as
 * propriedades já foram resolvidas mas nenhum bean foi criado ainda. É de
 * propósito — falhar aqui produz uma mensagem limpa, enquanto validar mais
 * tarde faria o erro de configuração aparecer depois de uma pilha de exceções
 * do Hibernate tentando conectar no banco, escondendo a causa real.
 * <p>
 * Existe como classe própria porque validar o ambiente não é trabalho do
 * {@code TokenService}, cuja responsabilidade é assinar e verificar token. Se um
 * dia entrarem outras exigências de configuração, o lugar delas é aqui.
 * <p>
 * Falha rápido e alto: subir com uma chave fraca ou vazia significa uma
 * aplicação que parece funcionar enquanto qualquer pessoa consegue forjar um
 * token de administrador. É melhor não subir.
 */
public class VerificadorDeConfiguracao
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    /**
     * HMAC256 aceita qualquer tamanho de chave, mas uma chave curta é quebrável
     * por força bruta. 32 caracteres é o piso razoável para o algoritmo.
     */
    static final int TAMANHO_MINIMO_DO_SEGREDO = 32;

    static final String PROPRIEDADE_SEGREDO = "api.security.token.secret";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
        validarSegredoJwt(evento.getEnvironment().getProperty(PROPRIEDADE_SEGREDO));
    }

    /**
     * Por último <b>dentro desta fase</b>, e não primeiro.
     * <p>
     * É contraintuitivo, então vale o registro: o próprio Spring carrega o
     * application.properties através de um listener deste mesmo evento
     * ({@code EnvironmentPostProcessorApplicationListener}). Rodar antes dele
     * significa ler a propriedade antes de ela existir — o verificador acusava
     * chave ausente mesmo com JWT_SECRET corretamente definida.
     * <p>
     * A fase inteira acontece antes de qualquer bean ser criado, então isto
     * continua falhando antes de tentar conectar no banco, que era o objetivo.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void validarSegredoJwt(String segredo) {
        if (segredo == null || segredo.isBlank()) {
            throw new ConfiguracaoInvalidaException(mensagem(
                    "A variável de ambiente JWT_SECRET não está definida."));
        }
        if (segredo.strip().length() < TAMANHO_MINIMO_DO_SEGREDO) {
            throw new ConfiguracaoInvalidaException(mensagem(String.format(
                    "A variável JWT_SECRET tem apenas %d caracteres; o mínimo é %d.",
                    segredo.strip().length(), TAMANHO_MINIMO_DO_SEGREDO)));
        }
    }

    /**
     * A mensagem traz o comando pronto para colar. Erro de configuração que só
     * diz "está errado" transfere para quem implanta o trabalho de descobrir o
     * como — e é nesse momento que se escolhe um valor fraco por pressa.
     */
    private static String mensagem(String problema) {
        return """

                ===============================================================
                 A APLICACAO NAO PODE SUBIR: configuracao de seguranca ausente
                ===============================================================

                 %s

                 JWT_SECRET e a chave que assina os tokens de login. Sem ela, ou
                 com uma chave fraca, qualquer pessoa consegue forjar um token de
                 administrador.

                 Gere um valor novo e unico para cada ambiente:

                   PowerShell:
                     $env:JWT_SECRET = -join ((48..57)+(97..122) | Get-Random -Count 64 | %% {[char]$_})

                   Linux ou macOS:
                     export JWT_SECRET=$(openssl rand -hex 32)

                 Em producao, defina a variavel no servico que inicia a aplicacao
                 (systemd, docker compose ou script de start), nunca em arquivo
                 versionado. Veja .env.example na raiz do projeto.

                ===============================================================
                """.formatted(problema);
    }

    /** Falha de configuração de ambiente, distinta de erro de regra de negócio. */
    public static class ConfiguracaoInvalidaException extends IllegalStateException {
        public ConfiguracaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
