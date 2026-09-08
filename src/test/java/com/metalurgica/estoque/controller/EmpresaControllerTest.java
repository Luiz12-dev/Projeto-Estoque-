package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.EmpresaRequest;
import com.metalurgica.estoque.dto.request.EmpresaUpdateRequest;
import com.metalurgica.estoque.dto.response.EmpresaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import com.metalurgica.estoque.service.EmpresaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmpresaController.class)
@WithMockUser(roles = "OPERADOR")
class EmpresaControllerTest extends TesteDeControlador {

    @MockitoBean
    private EmpresaService empresaService;

    private static EmpresaResponse empresa() {
        return new EmpresaResponse(
                2L, "Serralheria Silva", "12.345.678/0001-90", "44 99811-2200",
                "contato@silva.com.br", "Rua das Oficinas, 120", "Cliente antigo",
                LocalDateTime.parse("2026-08-01T09:00:00"),
                LocalDateTime.parse("2026-08-01T09:00:00"));
    }

    @Test
    @DisplayName("A empresa traz os campos que a tela e as OS consomem")
    void camposBatemComOModeloDoFrontend() throws Exception {
        when(empresaService.buscarPorId(2L)).thenReturn(empresa());

        mockMvc.perform(get("/api/empresas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.nome").value("Serralheria Silva"))
                .andExpect(jsonPath("$.cnpj").value("12.345.678/0001-90"))
                .andExpect(jsonPath("$.telefone").value("44 99811-2200"))
                .andExpect(jsonPath("$.email").value("contato@silva.com.br"))
                .andExpect(jsonPath("$.endereco").value("Rua das Oficinas, 120"))
                .andExpect(jsonPath("$.observacao").value("Cliente antigo"))
                .andExpect(jsonPath("$.criadoEm").exists())
                .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    @DisplayName("Empresa só com nome é aceita — o resto é opcional")
    void empresaSoComNomeEhAceita() throws Exception {
        // Na oficina o cadastro costuma começar só com o nome; exigir CNPJ
        // travaria o atendimento.
        when(empresaService.criar(any())).thenReturn(empresa());

        mockMvc.perform(post("/api/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmpresaRequest(
                                "Serralheria Silva", null, null, null, null, null)))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Nome com menos de 2 caracteres é recusado")
    void nomeCurtoEhRecusado() throws Exception {
        mockMvc.perform(post("/api/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmpresaRequest("X", null, null, null, null, null)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(empresaService, never()).criar(any());
    }

    @Test
    @DisplayName("CNPJ duplicado vira 409, para a tela poder explicar")
    void cnpjDuplicadoVira409() throws Exception {
        // Cair no 500 esconderia a causa; a pessoa tentaria de novo sem
        // entender que a empresa já existe.
        when(empresaService.criar(any()))
                .thenThrow(new DataIntegrityViolationException("cnpj duplicado"));

        mockMvc.perform(post("/api/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmpresaRequest(
                                "Serralheria Silva", "12.345.678/0001-90",
                                null, null, null, null)))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Empresa inexistente vira 404")
    void empresaInexistenteVira404() throws Exception {
        when(empresaService.buscarPorId(999L))
                .thenThrow(new RecursoNaoEncontradoException("Empresa não encontrada com ID: 999"));

        mockMvc.perform(get("/api/empresas/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A busca chega ao serviço e a listagem vem paginada")
    void buscaChegaAoServico() throws Exception {
        when(empresaService.listar(any(), any()))
                .thenReturn(new PageImpl<>(List.of(empresa()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/empresas").param("busca", "silva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Serralheria Silva"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(empresaService).listar(eq("silva"), any());
    }

    @Test
    @DisplayName("Atualizar devolve a empresa com os dados novos")
    void atualizarDevolveDadosNovos() throws Exception {
        when(empresaService.atualizar(eq(2L), any())).thenReturn(empresa());

        mockMvc.perform(put("/api/empresas/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new EmpresaUpdateRequest(
                                "Serralheria Silva", null, "44 99811-2200", null, null, null)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("44 99811-2200"));
    }
}
