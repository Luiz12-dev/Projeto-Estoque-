package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import com.metalurgica.estoque.dto.request.OrcamentoItemRequest;
import com.metalurgica.estoque.dto.request.OrcamentoRequest;
import com.metalurgica.estoque.dto.request.OrcamentoUpdateRequest;
import com.metalurgica.estoque.dto.request.SimulacaoOrcamentoRequest;
import com.metalurgica.estoque.dto.response.OrcamentoItemResponse;
import com.metalurgica.estoque.dto.response.OrcamentoResponse;
import com.metalurgica.estoque.dto.response.SimulacaoOrcamentoResponse;
import com.metalurgica.estoque.service.OrcamentoService;
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

@WebMvcTest(OrcamentoController.class)
class OrcamentoControllerTest extends TesteDeControlador {

    @MockitoBean
    private OrcamentoService orcamentoService;

    private static OrcamentoItemResponse item() {
        return new OrcamentoItemResponse(
                50L, "Flange furada", 4L, "Chapa Aço 3mm",
                new BigDecimal("200"), new BigDecimal("100"), 50,
                12, new BigDecimal("10"), null,
                new BigDecimal("1200"), new BigDecimal("3000"),
                new BigDecimal("695.00"), new BigDecimal("12.00"),
                new BigDecimal("0.9770"),
                new BigDecimal("3.86"), new BigDecimal("11.72"), new BigDecimal("15.58"),
                new BigDecimal("20.25"), new BigDecimal("1012.50"));
    }

    private static OrcamentoResponse orcamento() {
        return new OrcamentoResponse(
                1L, "ORC-0001", 2L, "Serralheria Silva",
                SituacaoOrcamento.PENDENTE, new BigDecimal("30"), new BigDecimal("1012.50"),
                "Prazo de 5 dias úteis", "Leo Fantineli",
                LocalDateTime.parse("2026-09-01T10:00:00"),
                LocalDateTime.parse("2026-09-01T10:00:00"),
                List.of(item()));
    }

    private static OrcamentoRequest requestValido() {
        return new OrcamentoRequest(2L, new BigDecimal("30"), "Prazo de 5 dias",
                List.of(new OrcamentoItemRequest("Flange", 4L,
                        new BigDecimal("200"), new BigDecimal("100"), 50, 12,
                        new BigDecimal("10"), null)));
    }

    // =====================================================================
    // Quem pode ver a margem — a regra que o dono da metalúrgica definiu
    // =====================================================================

    @Nested
    @DisplayName("Perfis")
    class Perfis {

        @Test
        @DisplayName("OPERADOR não lista orçamentos")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoLista() throws Exception {
            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isForbidden());
            verify(orcamentoService, never()).listar(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("OPERADOR não simula preço — a simulação também revela a margem")
        @WithMockUser(roles = "OPERADOR")
        void operadorNaoSimula() throws Exception {
            mockMvc.perform(post("/api/orcamentos/simular")
                            .contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ESCRITORIO lista — é ele quem monta o orçamento")
        @WithMockUser(roles = "ESCRITORIO")
        void escritorioLista() throws Exception {
            when(orcamentoService.listar(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/orcamentos")).andExpect(status().isOk());
        }
    }

    // =====================================================================
    // Formato do JSON — protege a sincronia manual com orcamento.model.ts
    // =====================================================================

    @Nested
    @DisplayName("Formato do JSON")
    @WithMockUser(roles = "ADMIN")
    class FormatoDoJson {

        @Test
        @DisplayName("O detalhe traz o preço aberto em parcelas, campo a campo")
        void detalheTrazPrecoAberto() throws Exception {
            when(orcamentoService.buscarPorId(1L)).thenReturn(orcamento());

            mockMvc.perform(get("/api/orcamentos/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value("ORC-0001"))
                    .andExpect(jsonPath("$.empresaId").value(2))
                    .andExpect(jsonPath("$.empresaNome").value("Serralheria Silva"))
                    .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                    .andExpect(jsonPath("$.margemPercentual").value(30))
                    .andExpect(jsonPath("$.valorTotal").value(1012.50))
                    .andExpect(jsonPath("$.usuarioNome").value("Leo Fantineli"))
                    // As parcelas que o cliente confere linha a linha:
                    .andExpect(jsonPath("$.itens[0].custoMaterialUnitario").value(3.86))
                    .andExpect(jsonPath("$.itens[0].custoCorteUnitario").value(11.72))
                    .andExpect(jsonPath("$.itens[0].subtotalUnitario").value(15.58))
                    .andExpect(jsonPath("$.itens[0].precoUnitario").value(20.25))
                    .andExpect(jsonPath("$.itens[0].precoTotal").value(1012.50))
                    .andExpect(jsonPath("$.itens[0].comprimentoCorteMetros").value(0.9770))
                    // Parâmetros congelados da chapa, que provam o preço no futuro:
                    .andExpect(jsonPath("$.itens[0].larguraChapaMm").value(1200))
                    .andExpect(jsonPath("$.itens[0].valorChapa").value(695.00))
                    .andExpect(jsonPath("$.itens[0].precoMetroCorte").value(12.00));
        }

        @Test
        @DisplayName("As parcelas somam o subtotal — a conta fecha no JSON, não só em memória")
        void parcelasSomamOSubtotal() throws Exception {
            when(orcamentoService.buscarPorId(1L)).thenReturn(orcamento());

            String corpo = mockMvc.perform(get("/api/orcamentos/1"))
                    .andReturn().getResponse().getContentAsString();

            var raiz = json.readTree(corpo).get("itens").get(0);
            BigDecimal material = raiz.get("custoMaterialUnitario").decimalValue();
            BigDecimal corte = raiz.get("custoCorteUnitario").decimalValue();
            BigDecimal subtotal = raiz.get("subtotalUnitario").decimalValue();

            org.assertj.core.api.Assertions.assertThat(material.add(corte))
                    .as("material + corte tem de dar o subtotal exibido ao cliente")
                    .isEqualByComparingTo(subtotal);
        }

        @Test
        @DisplayName("A listagem vem sem itens, de propósito")
        void listagemVemSemItens() throws Exception {
            // Carregar as peças de cada linha seria N+1. A tela de lista mostra
            // só código, empresa, situação e total.
            OrcamentoResponse semItens = new OrcamentoResponse(
                    1L, "ORC-0001", 2L, "Serralheria Silva", SituacaoOrcamento.PENDENTE,
                    new BigDecimal("30"), new BigDecimal("1012.50"), null, "Leo",
                    LocalDateTime.parse("2026-09-01T10:00:00"),
                    LocalDateTime.parse("2026-09-01T10:00:00"), List.of());
            when(orcamentoService.listar(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(semItens), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/orcamentos"))
                    .andExpect(jsonPath("$.content[0].valorTotal").value(1012.50))
                    .andExpect(jsonPath("$.content[0].itens").isEmpty());
        }

        @Test
        @DisplayName("A simulação não tem identidade — nada foi gravado")
        void simulacaoNaoTemIdentidade() throws Exception {
            when(orcamentoService.simular(any())).thenReturn(new SimulacaoOrcamentoResponse(
                    new BigDecimal("30"), new BigDecimal("719.00"),
                    List.of(new OrcamentoItemResponse(
                            null, "Flange", 4L, "Chapa Aço 3mm",
                            new BigDecimal("200"), new BigDecimal("100"), 50,
                            null, null, null,
                            new BigDecimal("1200"), new BigDecimal("3000"),
                            new BigDecimal("695.00"), new BigDecimal("12.00"),
                            new BigDecimal("0.6000"),
                            new BigDecimal("3.86"), new BigDecimal("7.20"),
                            new BigDecimal("11.06"), new BigDecimal("14.38"),
                            new BigDecimal("719.00")))));

            SimulacaoOrcamentoRequest request = new SimulacaoOrcamentoRequest(
                    new BigDecimal("30"),
                    List.of(new OrcamentoItemRequest("Flange", 4L,
                            new BigDecimal("200"), new BigDecimal("100"), 50, null, null, null)));

            mockMvc.perform(post("/api/orcamentos/simular")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valorTotal").value(719.00))
                    .andExpect(jsonPath("$.itens[0].id").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    // =====================================================================
    // Validação de entrada
    // =====================================================================

    @Nested
    @DisplayName("Validação")
    @WithMockUser(roles = "ADMIN")
    class Validacao {

        @Test
        @DisplayName("Orçamento válido devolve 201")
        void orcamentoValidoDevolve201() throws Exception {
            when(orcamentoService.criar(any())).thenReturn(orcamento());

            mockMvc.perform(post("/api/orcamentos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(requestValido())).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.codigo").value("ORC-0001"));
        }

        @Test
        @DisplayName("Orçamento sem nenhuma peça é recusado")
        void orcamentoSemPecasEhRecusado() throws Exception {
            OrcamentoRequest vazio = new OrcamentoRequest(
                    2L, new BigDecimal("30"), null, List.of());

            mockMvc.perform(post("/api/orcamentos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(vazio)).with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(orcamentoService, never()).criar(any());
        }

        @Test
        @DisplayName("Margem negativa é recusada")
        void margemNegativaEhRecusada() throws Exception {
            OrcamentoRequest negativa = new OrcamentoRequest(
                    2L, new BigDecimal("-10"), null,
                    List.of(new OrcamentoItemRequest("Flange", 4L,
                            new BigDecimal("200"), new BigDecimal("100"), 1, null, null, null)));

            mockMvc.perform(post("/api/orcamentos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(negativa)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Peça com medida zero é recusada")
        void pecaComMedidaZeroEhRecusada() throws Exception {
            OrcamentoRequest zerada = new OrcamentoRequest(
                    2L, new BigDecimal("30"), null,
                    List.of(new OrcamentoItemRequest("Flange", 4L,
                            BigDecimal.ZERO, new BigDecimal("100"), 1, null, null, null)));

            mockMvc.perform(post("/api/orcamentos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(zerada)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Chapa sem parâmetros vira 400 com a mensagem que ensina onde resolver")
        void chapaSemParametrosViraMensagemUtil() throws Exception {
            when(orcamentoService.criar(any())).thenThrow(new IllegalArgumentException(
                    "A chapa 'Chapa Aço 3mm' não pode ser usada em orçamento: falta preço "
                    + "por metro de corte. Preencha os parâmetros de corte na tela de Produtos."));

            mockMvc.perform(post("/api/orcamentos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(requestValido())).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem")
                            .value(org.hamcrest.Matchers.containsString("tela de Produtos")));
        }
    }

    // =====================================================================
    // Filtros e atualização
    // =====================================================================

    @Nested
    @DisplayName("Filtros e situação")
    @WithMockUser(roles = "ADMIN")
    class FiltrosESituacao {

        @Test
        @DisplayName("Os filtros da tela chegam ao serviço")
        void filtrosChegamAoServico() throws Exception {
            when(orcamentoService.listar(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/orcamentos")
                            .param("busca", "ORC")
                            .param("empresaId", "2")
                            .param("situacao", "APROVADO"))
                    .andExpect(status().isOk());

            verify(orcamentoService).listar(
                    eq("ORC"), eq(2L), eq(SituacaoOrcamento.APROVADO), any(), any(), any());
        }

        @Test
        @DisplayName("Situação inválida vira 400, não 500")
        void situacaoInvalidaVira400() throws Exception {
            mockMvc.perform(get("/api/orcamentos").param("situacao", "INEXISTENTE"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Aprovar um orçamento devolve a situação nova")
        void aprovarDevolveSituacaoNova() throws Exception {
            OrcamentoResponse aprovado = new OrcamentoResponse(
                    1L, "ORC-0001", 2L, "Serralheria Silva", SituacaoOrcamento.APROVADO,
                    new BigDecimal("30"), new BigDecimal("1012.50"), null, "Leo",
                    LocalDateTime.parse("2026-09-01T10:00:00"),
                    LocalDateTime.parse("2026-09-02T09:00:00"), List.of());
            when(orcamentoService.atualizar(eq(1L), any())).thenReturn(aprovado);

            mockMvc.perform(put("/api/orcamentos/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(new OrcamentoUpdateRequest(
                                    SituacaoOrcamento.APROVADO, null)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.situacao").value("APROVADO"));
        }
    }
}
