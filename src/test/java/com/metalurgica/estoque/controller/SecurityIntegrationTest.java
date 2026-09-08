package com.metalurgica.estoque.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.metalurgica.estoque.TesteDeIntegracao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração para a camada de segurança (JWT + Spring Security).
 * Valida que endpoints protegidos rejeitam requests sem token e que
 * endpoints públicos são acessíveis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest extends TesteDeIntegracao {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Endpoints Protegidos")
    class EndpointsProtegidos {

        @Test
        @DisplayName("Deve retornar 403 ao acessar /api/dashboard sem token JWT")
        void deveRetornar403AoAcessarDashboardSemToken() throws Exception {
            // Arrange — nenhum token configurado

            // Act & Assert
            mockMvc.perform(get("/api/dashboard")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Deve retornar 403 ao acessar /api/produtos sem token JWT")
        void deveRetornar403AoAcessarProdutosSemToken() throws Exception {
            // Arrange — nenhum token configurado

            // Act & Assert
            mockMvc.perform(get("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Deve retornar 403 ao acessar /api/movimentacoes sem token JWT")
        void deveRetornar403AoAcessarMovimentacoesSemToken() throws Exception {
            // Arrange — nenhum token configurado

            // Act & Assert
            mockMvc.perform(get("/api/movimentacoes")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("A tela precisa ser publica")
    class TelaPublica {

        /**
         * Regressao de um defeito que so apareceu ao rodar o pacote instalado:
         * com a tela servida pela propria aplicacao e {@code anyRequest()
         * .authenticated()}, o index.html e o JavaScript exigiam token. Ninguem
         * tem token antes de entrar, entao a pagina de login nunca aparecia —
         * a instalacao inteira mostrava 403 numa tela em branco.
         */

        @Test
        @DisplayName("A pagina inicial nao e barrada pela seguranca")
        void paginaInicialNaoEhBarrada() throws Exception {
            // O status exato depende de a tela compilada estar em static/ ou
            // nao — 200 quando esta, 404 quando so o backend foi compilado.
            // O que este teste protege e outra coisa: nunca 403.
            naoPodeSer403("/");
        }

        @Test
        @DisplayName("Arquivos da tela nao sao barrados pela seguranca")
        void arquivosDaTelaNaoSaoBarrados() throws Exception {
            for (String caminho : new String[] { "/index.html", "/main.js", "/styles.css",
                    "/favicon.ico", "/login", "/cortes" }) {
                naoPodeSer403(caminho);
            }
        }

        private void naoPodeSer403(String caminho) throws Exception {
            mockMvc.perform(get(caminho)).andExpect(result -> {
                if (result.getResponse().getStatus() == 403) {
                    throw new AssertionError("A seguranca barrou " + caminho + " com 403. "
                            + "Sem esses arquivos a tela de login nao carrega, e a "
                            + "instalacao mostra uma pagina em branco.");
                }
            });
        }

        @Test
        @DisplayName("Mas a API continua protegida")
        void apiContinuaProtegida() throws Exception {
            mockMvc.perform(get("/api/produtos")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Margem de lucro por perfil")
    class MargemDeLucroPorPerfil {

        /**
         * O corte que o dono da metalúrgica pediu: preço de venda e margem são
         * informação dos quatro sócios e de quem monta orçamento. Quem está no
         * chão de fábrica registra material e corte, mas não vê por quanto a
         * peça é vendida.
         */

        @Test
        @DisplayName("OPERADOR não enxerga orçamentos")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoVeOrcamentos() throws Exception {
            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERADOR não simula preço — a simulação também revela a margem")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoSimulaPreco() throws Exception {
            // Bloquear só a listagem deixaria a margem acessível pela simulação.
            mockMvc.perform(post("/api/orcamentos/simular")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERADOR não enxerga o dashboard")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoVeDashboard() throws Exception {
            mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ESCRITORIO enxerga orçamentos — é ele quem os monta")
        @WithMockUser(roles = "ESCRITORIO")
        void escritorioVeOrcamentos() throws Exception {
            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("ESCRITORIO NÃO administra contas de acesso")
        @WithMockUser(roles = "ESCRITORIO")
        void escritorioNaoAdministraUsuarios() throws Exception {
            // A distinção entre os quatro sócios e o funcionário do escritório
            // existe exatamente aqui.
            mockMvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN enxerga tudo")
        @WithMockUser(roles = "ADMIN")
        void adminVeTudo() throws Exception {
            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isOk());
            mockMvc.perform(get("/api/usuarios")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("OPERADOR continua registrando material e corte")
        @WithMockUser(roles = "OPERADOR")
        void operadorContinuaTrabalhando() throws Exception {
            // A restrição é sobre preço de venda, não sobre o trabalho do dia a dia.
            mockMvc.perform(get("/api/produtos")).andExpect(status().isOk());
            mockMvc.perform(get("/api/movimentacoes")).andExpect(status().isOk());
            mockMvc.perform(get("/api/pecas-cortadas")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Endpoints Públicos")
    class EndpointsPublicos {

        @Test
        @DisplayName("Deve permitir POST em /api/auth/login sem token (retorna 400 por validação, não 403)")
        void devePermitirLoginSemToken() throws Exception {
            // Arrange — JSON inválido para trigger de @Valid, mas que prove que a segurança não bloqueia

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
