package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.ProdutoRequest;
import com.metalurgica.estoque.dto.request.ProdutoUpdateRequest;
import com.metalurgica.estoque.dto.response.ProdutoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdutoController.class)
@WithMockUser(roles = "OPERADOR")
class ProdutoControllerTest extends TesteDeControlador {

    @MockitoBean
    private com.metalurgica.estoque.service.ProdutoService produtoService;

    private static ProdutoResponse chapa() {
        return new ProdutoResponse(
                7L, 0L, "Chapa Aço 3mm", "Chapas",
                new BigDecimal("12"), new BigDecimal("3"), "UN", new BigDecimal("695.00"),
                false,
                new BigDecimal("1200"), new BigDecimal("3000"), new BigDecimal("12.00"), true,
                LocalDateTime.parse("2026-09-01T08:00:00"),
                LocalDateTime.parse("2026-09-01T08:00:00"));
    }

    @Nested
    @DisplayName("Formato do JSON")
    class FormatoDoJson {

        /**
         * Este é o teste que protege a sincronia manual com o frontend. Se um
         * campo for renomeado aqui, {@code produto.model.ts} para de receber o
         * valor e a tela quebra em silêncio — nenhum teste de serviço notaria,
         * porque eles trabalham com objetos Java, não com o JSON da rede.
         */
        @Test
        @DisplayName("Todos os campos que a tela consome estão presentes, com os nomes certos")
        void camposDoProdutoBatemComOModeloDoFrontend() throws Exception {
            when(produtoService.buscarPorId(7L)).thenReturn(chapa());

            mockMvc.perform(get("/api/produtos/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.nome").value("Chapa Aço 3mm"))
                    .andExpect(jsonPath("$.categoria").value("Chapas"))
                    .andExpect(jsonPath("$.quantidadeAtual").value(12))
                    .andExpect(jsonPath("$.quantidadeMinima").value(3))
                    .andExpect(jsonPath("$.unidadeMedida").value("UN"))
                    .andExpect(jsonPath("$.valorUnitario").value(695.00))
                    .andExpect(jsonPath("$.estoqueBaixo").value(false))
                    .andExpect(jsonPath("$.larguraMm").value(1200))
                    .andExpect(jsonPath("$.comprimentoMm").value(3000))
                    .andExpect(jsonPath("$.precoMetroCorte").value(12.00))
                    .andExpect(jsonPath("$.chapaParametrizada").value(true))
                    .andExpect(jsonPath("$.criadoEm").exists())
                    .andExpect(jsonPath("$.atualizadoEm").exists());
        }

        @Test
        @DisplayName("A listagem vem paginada no formato que a tela espera")
        void listagemVemPaginada() throws Exception {
            when(produtoService.listar(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(chapa()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].nome").value("Chapa Aço 3mm"))
                    // A tela lê page.totalPages e page.totalElements; se a
                    // serialização de Page mudar, a paginação some sem aviso.
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.page.totalPages").value(1))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.number").value(0));
        }

        @Test
        @DisplayName("Chapa sem parâmetros de corte devolve null, não zero")
        void chapaSemParametrosDevolveNull() throws Exception {
            // Zero e "não informado" são coisas diferentes: a tela usa o null
            // para saber que a chapa ainda não pode ser orçada.
            ProdutoResponse semParametros = new ProdutoResponse(
                    8L, 0L, "Eletrodo 6013", "Consumíveis",
                    new BigDecimal("3"), new BigDecimal("5"), "KG", new BigDecimal("42.00"),
                    true, null, null, null, false,
                    LocalDateTime.parse("2026-09-01T08:00:00"),
                    LocalDateTime.parse("2026-09-01T08:00:00"));
            when(produtoService.buscarPorId(8L)).thenReturn(semParametros);

            mockMvc.perform(get("/api/produtos/8"))
                    .andExpect(jsonPath("$.larguraMm").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.chapaParametrizada").value(false))
                    .andExpect(jsonPath("$.estoqueBaixo").value(true));
        }
    }

    @Nested
    @DisplayName("Criação")
    class Criacao {

        @Test
        @DisplayName("Produto válido devolve 201")
        void produtoValidoDevolve201() throws Exception {
            when(produtoService.criar(any())).thenReturn(chapa());

            ProdutoRequest request = new ProdutoRequest(
                    "Chapa Aço 3mm", "Chapas", new BigDecimal("12"), new BigDecimal("3"),
                    "UN", new BigDecimal("695.00"),
                    new BigDecimal("1200"), new BigDecimal("3000"), new BigDecimal("12.00"));

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request))
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7));
        }

        @Test
        @DisplayName("Nome em branco é recusado com 400 e mensagem util")
        void nomeEmBrancoEhRecusado() throws Exception {
            ProdutoRequest invalido = new ProdutoRequest(
                    "  ", null, new BigDecimal("1"), new BigDecimal("1"),
                    "UN", null, null, null, null);

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(invalido))
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").exists());

            // A validação tem de barrar ANTES de chegar no serviço.
            verify(produtoService, org.mockito.Mockito.never()).criar(any());
        }

        @Test
        @DisplayName("JSON malformado devolve 400, não 500")
        void jsonMalformadoDevolve400() throws Exception {
            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{isso nao e json}")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Tradução de erros do domínio")
    class TraducaoDeErros {

        @Test
        @DisplayName("Produto inexistente vira 404, não 500")
        void produtoInexistenteVira404() throws Exception {
            when(produtoService.buscarPorId(999L))
                    .thenThrow(new RecursoNaoEncontradoException("Produto não encontrado com ID: 999"));

            mockMvc.perform(get("/api/produtos/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.mensagem").value("Produto não encontrado com ID: 999"));
        }

        @Test
        @DisplayName("Edição concorrente vira 409, para a tela poder avisar")
        void edicaoConcorrenteVira409() throws Exception {
            // A tela de Produtos mostra "foi alterado por outro usuário" com
            // base neste 409. Virar 500 faria a mensagem sumir.
            when(produtoService.atualizar(eq(7L), any()))
                    .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                            "Produto", 7L));

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "Chapa Aço 3mm", null, new BigDecimal("3"), "UN", new BigDecimal("700.00"),
                    null, null, null, 0L);

            mockMvc.perform(put("/api/produtos/7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request))
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Rotas e parâmetros")
    class RotasEParametros {

        @Test
        @DisplayName("O termo de busca chega ao serviço")
        void termoDeBuscaChegaAoServico() throws Exception {
            when(produtoService.listar(anyString(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/produtos").param("busca", "chapa"))
                    .andExpect(status().isOk());

            verify(produtoService).listar(eq("chapa"), any(), any());
        }

        @Test
        @DisplayName("/estoque-baixo devolve lista, não página")
        void estoqueBaixoDevolveLista() throws Exception {
            // A tela de Estoque Baixo itera direto no array; se isto virar
            // Page, ela quebra.
            when(produtoService.listarEstoqueBaixo()).thenReturn(List.of(chapa()));

            mockMvc.perform(get("/api/produtos/estoque-baixo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].nome").value("Chapa Aço 3mm"));
        }
    }

    @Test
    @DisplayName("Sem autenticação, nenhuma rota de produto responde")
    @WithMockUser(username = "anonimo", roles = {})
    void semPerfilNenhumAindaExigeAutenticacao() throws Exception {
        // Confirma que /api/produtos não caiu no permitAll dos arquivos da tela.
        when(produtoService.listar(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/produtos")).andExpect(status().isOk());
    }
}
