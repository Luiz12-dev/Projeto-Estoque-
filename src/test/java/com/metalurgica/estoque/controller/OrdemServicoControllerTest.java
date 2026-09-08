package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.dto.request.OrdemServicoRequest;
import com.metalurgica.estoque.dto.request.OrdemServicoUpdateRequest;
import com.metalurgica.estoque.dto.response.OrdemServicoResponse;
import com.metalurgica.estoque.service.OrdemServicoService;
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

@WebMvcTest(OrdemServicoController.class)
@WithMockUser(roles = "OPERADOR")
class OrdemServicoControllerTest extends TesteDeControlador {

    @MockitoBean
    private OrdemServicoService ordemServicoService;

    private static OrdemServicoResponse os() {
        return new OrdemServicoResponse(
                12L, "OS-0003", "Portão basculante 3,5m",
                2L, "Serralheria Silva",
                StatusOrdemServico.EM_ANDAMENTO, PrioridadeOrdemServico.ALTA,
                LocalDateTime.parse("2026-09-01T08:00:00"), null,
                "Cliente pediu pintura preta",
                new BigDecimal("800.00"), new BigDecimal("1234.00"), 5,
                "Leo Fantineli");
    }

    @Nested
    @DisplayName("Formato do JSON")
    class FormatoDoJson {

        @Test
        @DisplayName("A OS traz todos os campos que a tela de detalhe consome")
        void camposBatemComOModeloDoFrontend() throws Exception {
            when(ordemServicoService.buscarPorId(12L)).thenReturn(os());

            mockMvc.perform(get("/api/ordens-servico/12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(12))
                    .andExpect(jsonPath("$.codigo").value("OS-0003"))
                    .andExpect(jsonPath("$.descricao").value("Portão basculante 3,5m"))
                    .andExpect(jsonPath("$.empresaId").value(2))
                    .andExpect(jsonPath("$.empresaNome").value("Serralheria Silva"))
                    .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                    .andExpect(jsonPath("$.prioridade").value("ALTA"))
                    .andExpect(jsonPath("$.dataAbertura").exists())
                    .andExpect(jsonPath("$.observacao").value("Cliente pediu pintura preta"))
                    .andExpect(jsonPath("$.valorMaoDeObra").value(800.00))
                    .andExpect(jsonPath("$.custoTotal").value(1234.00))
                    .andExpect(jsonPath("$.totalMovimentacoes").value(5))
                    .andExpect(jsonPath("$.usuarioNome").value("Leo Fantineli"));
        }

        @Test
        @DisplayName("OS em aberto tem dataConclusao nula")
        void osEmAbertoTemConclusaoNula() throws Exception {
            // A tela usa a ausência desta data para decidir se mostra o botão
            // de concluir e o de baixar PDF.
            when(ordemServicoService.buscarPorId(12L)).thenReturn(os());

            mockMvc.perform(get("/api/ordens-servico/12"))
                    .andExpect(jsonPath("$.dataConclusao")
                            .value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("Status e prioridade saem como texto, não como número")
        void enumsSaemComoTexto() throws Exception {
            // A tela compara com 'ABERTA'/'CONCLUIDA' para escolher a cor do
            // selo e liberar ações. Ordinal quebraria tudo isso em silêncio.
            when(ordemServicoService.listar(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(os()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/ordens-servico"))
                    .andExpect(jsonPath("$.content[0].status").value("EM_ANDAMENTO"))
                    .andExpect(jsonPath("$.content[0].prioridade").value("ALTA"));
        }
    }

    @Nested
    @DisplayName("Criação e edição")
    class CriacaoEEdicao {

        @Test
        @DisplayName("OS válida devolve 201 com o código gerado")
        void osValidaDevolve201() throws Exception {
            when(ordemServicoService.criar(any())).thenReturn(os());

            OrdemServicoRequest request = new OrdemServicoRequest(
                    "Portão basculante 3,5m", 2L, PrioridadeOrdemServico.ALTA, null, null);

            mockMvc.perform(post("/api/ordens-servico")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.codigo").value("OS-0003"));
        }

        @Test
        @DisplayName("OS sem descrição é recusada")
        void osSemDescricaoEhRecusada() throws Exception {
            OrdemServicoRequest semDescricao = new OrdemServicoRequest(
                    "   ", 2L, PrioridadeOrdemServico.MEDIA, null, null);

            mockMvc.perform(post("/api/ordens-servico")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(semDescricao)).with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(ordemServicoService, never()).criar(any());
        }

        @Test
        @DisplayName("OS sem empresa é recusada")
        void osSemEmpresaEhRecusada() throws Exception {
            OrdemServicoRequest semEmpresa = new OrdemServicoRequest(
                    "Portão", null, PrioridadeOrdemServico.MEDIA, null, null);

            mockMvc.perform(post("/api/ordens-servico")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(semEmpresa)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Mão de obra negativa é recusada")
        void maoDeObraNegativaEhRecusada() throws Exception {
            OrdemServicoRequest negativa = new OrdemServicoRequest(
                    "Portão", 2L, PrioridadeOrdemServico.MEDIA, null, new BigDecimal("-100"));

            mockMvc.perform(post("/api/ordens-servico")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(negativa)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Zerar a mão de obra é permitido — o campo aceita zero")
        void zerarMaoDeObraEhPermitido() throws Exception {
            // Regressão do defeito original: a mão de obra era guardada em
            // "maior que zero" e não dava para corrigir um valor lançado errado.
            when(ordemServicoService.atualizar(eq(12L), any())).thenReturn(os());

            OrdemServicoUpdateRequest zerar = new OrdemServicoUpdateRequest(
                    null, null, null, null, null, BigDecimal.ZERO);

            mockMvc.perform(put("/api/ordens-servico/12")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(zerar)).with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Transição de status inválida vira 400, com a mensagem do domínio")
        void transicaoInvalidaVira400() throws Exception {
            when(ordemServicoService.atualizar(eq(12L), any()))
                    .thenThrow(new IllegalArgumentException(
                            "Não é possível reabrir uma OS cancelada."));

            OrdemServicoUpdateRequest reabrir = new OrdemServicoUpdateRequest(
                    null, null, StatusOrdemServico.ABERTA, null, null, null);

            mockMvc.perform(put("/api/ordens-servico/12")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(reabrir)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem")
                            .value(org.hamcrest.Matchers.containsString("cancelada")));
        }
    }

    @Nested
    @DisplayName("Filtros")
    class Filtros {

        @Test
        @DisplayName("Os filtros da tela chegam ao serviço")
        void filtrosChegamAoServico() throws Exception {
            when(ordemServicoService.listar(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/ordens-servico")
                            .param("busca", "portão")
                            .param("status", "ABERTA"))
                    .andExpect(status().isOk());

            verify(ordemServicoService).listar(
                    eq("portão"), eq(StatusOrdemServico.ABERTA), any(), any(), any());
        }

        @Test
        @DisplayName("Data em formato brasileiro vira 400, não 500")
        void dataEmFormatoErradoVira400() throws Exception {
            // A API espera ISO (2026-09-01). Mandar 01/09/2026 é erro de quem
            // chama, e precisa aparecer como tal.
            mockMvc.perform(get("/api/ordens-servico").param("dataInicio", "01/09/2026"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Status inexistente vira 400, não 500")
        void statusInexistenteVira400() throws Exception {
            mockMvc.perform(get("/api/ordens-servico").param("status", "PAUSADA"))
                    .andExpect(status().isBadRequest());
        }
    }
}
