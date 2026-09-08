package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.Role;
import com.metalurgica.estoque.dto.request.UsuarioRequest;
import com.metalurgica.estoque.dto.request.UsuarioUpdateRequest;
import com.metalurgica.estoque.dto.response.UsuarioResponse;
import com.metalurgica.estoque.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest extends TesteDeControlador {

    @MockitoBean
    private UsuarioService usuarioService;

    private static UsuarioResponse leo() {
        return new UsuarioResponse(1L, "Leo Fantineli", "leo", Role.ADMIN);
    }

    @Nested
    @DisplayName("Só os sócios administram contas")
    class SomenteAdmin {

        @Test
        @DisplayName("OPERADOR não lista usuários")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoLista() throws Exception {
            mockMvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
            verify(usuarioService, never()).listar();
        }

        @Test
        @DisplayName("ESCRITORIO também não — é a distinção entre sócio e funcionário")
        @WithMockUser(roles = "ESCRITORIO")
        void escritorioNaoLista() throws Exception {
            // Quem monta orçamento vê a margem, mas não cria nem apaga acesso.
            // Foi o dono quem separou "os quatro sócios" do "funcionário".
            mockMvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERADOR não cria usuário")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoCria() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(new UsuarioRequest("João", "joao", "senha123",
                                    Role.OPERADOR)))
                            .with(csrf()))
                    .andExpect(status().isForbidden());

            verify(usuarioService, never()).criar(any());
        }

        @Test
        @DisplayName("ADMIN lista")
        @WithMockUser(roles = "ADMIN")
        void adminLista() throws Exception {
            when(usuarioService.listar()).thenReturn(List.of(leo()));

            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].login").value("leo"));
        }
    }

    @Nested
    @DisplayName("Formato e validação")
    @WithMockUser(roles = "ADMIN")
    class FormatoEValidacao {

        @Test
        @DisplayName("A senha NUNCA aparece na resposta")
        void senhaNuncaApareceNaResposta() throws Exception {
            // UsuarioResponse não tem o campo, e este teste existe para que
            // ninguém o acrescente por engano ao mexer no record.
            when(usuarioService.listar()).thenReturn(List.of(leo()));

            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(jsonPath("$[0].senha").doesNotExist())
                    .andExpect(jsonPath("$[0].password").doesNotExist())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Leo Fantineli"))
                    .andExpect(jsonPath("$[0].role").value("ADMIN"));
        }

        @Test
        @DisplayName("Os três perfis são aceitos")
        void tresPerfisSaoAceitos() throws Exception {
            when(usuarioService.criar(any())).thenReturn(leo());

            for (Role perfil : Role.values()) {
                mockMvc.perform(post("/api/usuarios")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(new UsuarioRequest(
                                        "Fulano", "fulano", "senha123", perfil)))
                                .with(csrf()))
                        .andExpect(status().isCreated());
            }
        }

        @Test
        @DisplayName("Usuário sem perfil é recusado")
        void usuarioSemPerfilEhRecusado() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(new UsuarioRequest("Fulano", "fulano", "senha123", null)))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(usuarioService, never()).criar(any());
        }

        @Test
        @DisplayName("Login com menos de 3 caracteres é recusado")
        void loginCurtoEhRecusado() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(new UsuarioRequest("Fulano", "ab", "senha123",
                                    Role.OPERADOR)))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login duplicado vira 409, não 500")
        void loginDuplicadoVira409() throws Exception {
            when(usuarioService.criar(any()))
                    .thenThrow(new DataIntegrityViolationException("login duplicado"));

            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(new UsuarioRequest("Outro Leo", "leo", "senha123",
                                    Role.OPERADOR)))
                            .with(csrf()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Perfil inexistente vira 400, não 500")
        void perfilInexistenteVira400() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Fulano","login":"fulano","senha":"senha123",
                                     "role":"GERENTE"}""")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }
}
