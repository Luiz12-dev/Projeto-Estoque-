package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.LoginRequest;
import com.metalurgica.estoque.dto.response.TokenResponse;
import com.metalurgica.estoque.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends TesteDeControlador {

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("O login é público — sem ele ninguém entraria")
    void loginEhPublico() throws Exception {
        // Nenhum @WithMockUser aqui de propósito: esta é a única rota que
        // precisa funcionar para quem ainda não tem token.
        when(authService.login(any())).thenReturn(new TokenResponse("um.token.jwt", "ADMIN"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequest("leo", "senha123")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("um.token.jwt"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("A resposta traz o perfil — a tela decide o menu por ele")
    void respostaTrazOPerfil() throws Exception {
        // O frontend guarda o role no localStorage e esconde Dashboard,
        // Usuários e a aba de Orçamentos com base nele.
        when(authService.login(any())).thenReturn(new TokenResponse("t", "OPERADOR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequest("joao", "senha123")))
                        .with(csrf()))
                .andExpect(jsonPath("$.role").value("OPERADOR"));
    }

    @Test
    @DisplayName("Credencial errada vira 400, nunca 500")
    void credencialErradaVira400() throws Exception {
        // O AuthService lança IllegalArgumentException com a mesma mensagem
        // para login inexistente e senha errada, de propósito: dizer qual dos
        // dois falhou entregaria a um atacante a lista de logins válidos.
        //
        // O status importa para a tela: a correção do login passou a
        // distinguir 400 (credencial errada) de 0 (sistema fora do ar) e de
        // 429 (bloqueado por tentativas). Virar 500 apagaria essa distinção.
        when(authService.login(any()))
                .thenThrow(new IllegalArgumentException("Login ou senha inválidos"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequest("leo", "errada")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Login ou senha inválidos"));
    }

    @Test
    @DisplayName("Login inexistente e senha errada dão a MESMA resposta")
    void loginInexistenteESenhaErradaSaoIndistinguiveis() throws Exception {
        // Se as respostas diferissem, daria para descobrir quais logins existem
        // testando um por um.
        when(authService.login(any()))
                .thenThrow(new IllegalArgumentException("Login ou senha inválidos"));

        for (LoginRequest tentativa : new LoginRequest[] {
                new LoginRequest("naoexiste", "qualquer"),
                new LoginRequest("leo", "errada") }) {

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(tentativa)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("Login ou senha inválidos"));
        }
    }

    @Test
    @DisplayName("Login em branco é recusado antes de tocar o serviço")
    void loginEmBrancoEhRecusado() throws Exception {
        // Sem isto, um formulário vazio consumiria uma das 10 tentativas que o
        // limitador permite por IP.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new LoginRequest("", "")))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("Corpo ausente vira 400, não 500")
    void corpoAusenteVira400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
