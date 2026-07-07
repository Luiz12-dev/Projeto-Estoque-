package com.metalurgica.estoque.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
class SecurityIntegrationTest {

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
