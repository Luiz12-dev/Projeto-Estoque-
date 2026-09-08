package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.PecaCortadaRequest;
import com.metalurgica.estoque.dto.response.PecaCortadaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import com.metalurgica.estoque.service.PecaCortadaService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PecaCortadaController.class)
@WithMockUser(roles = "OPERADOR")
class PecaCortadaControllerTest extends TesteDeControlador {

    @MockitoBean
    private PecaCortadaService pecaCortadaService;

    private static PecaCortadaResponse peca() {
        return new PecaCortadaResponse(
                30L, "Flange furada",
                new BigDecimal("1200"), new BigDecimal("3000"), new BigDecimal("695.00"),
                new BigDecimal("200"), new BigDecimal("100"), 2,
                new BigDecimal("3.86"), new BigDecimal("7.72"),
                4L, "Chapa Aço 3mm",
                12L, "OS-0003",
                2L, "Serralheria Silva",
                100L, "Leo Fantineli",
                LocalDateTime.parse("2026-09-01T14:30:00"));
    }

    @Nested
    @DisplayName("Formato do JSON")
    class FormatoDoJson {

        @Test
        @DisplayName("A peça traz empresa e OS — foi o que destravou a consulta por empresa")
        void trazEmpresaEOrdemServico() throws Exception {
            when(pecaCortadaService.listar(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(peca()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/pecas-cortadas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].nome").value("Flange furada"))
                    .andExpect(jsonPath("$.content[0].quantidade").value(2))
                    .andExpect(jsonPath("$.content[0].valorUnitarioCalculado").value(3.86))
                    .andExpect(jsonPath("$.content[0].valorTotalCalculado").value(7.72))
                    .andExpect(jsonPath("$.content[0].produtoNome").value("Chapa Aço 3mm"))
                    // Estes quatro só existem porque a aba Cortes precisa
                    // responder "o que já cortei para a empresa X":
                    .andExpect(jsonPath("$.content[0].ordemServicoId").value(12))
                    .andExpect(jsonPath("$.content[0].ordemServicoCodigo").value("OS-0003"))
                    .andExpect(jsonPath("$.content[0].empresaId").value(2))
                    .andExpect(jsonPath("$.content[0].empresaNome").value("Serralheria Silva"));
        }

        @Test
        @DisplayName("As medidas gravadas vêm junto — é o que prova o custo depois")
        void medidasGravadasVemJunto() throws Exception {
            when(pecaCortadaService.listar(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(peca()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/pecas-cortadas"))
                    .andExpect(jsonPath("$.content[0].larguraChapaMm").value(1200))
                    .andExpect(jsonPath("$.content[0].comprimentoChapaMm").value(3000))
                    .andExpect(jsonPath("$.content[0].valorChapa").value(695.00))
                    .andExpect(jsonPath("$.content[0].larguraPecaMm").value(200))
                    .andExpect(jsonPath("$.content[0].comprimentoPecaMm").value(100));
        }

        @Test
        @DisplayName("A listagem por OS é lista, não página")
        void listagemPorOsEhLista() throws Exception {
            // O detalhe da OS itera direto no array. Virar Page quebraria a
            // seção de peças cortadas sem nenhum aviso.
            when(pecaCortadaService.listarPorOrdemServico(12L)).thenReturn(List.of(peca()));

            mockMvc.perform(get("/api/ordens-servico/12/pecas-cortadas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].nome").value("Flange furada"));
        }
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("Corte válido devolve 201")
        void corteValidoDevolve201() throws Exception {
            when(pecaCortadaService.criar(any())).thenReturn(peca());

            PecaCortadaRequest request = new PecaCortadaRequest(
                    "Flange furada", new BigDecimal("1200"), new BigDecimal("3000"),
                    new BigDecimal("695.00"), new BigDecimal("200"), new BigDecimal("100"),
                    2, 4L, 12L);

            mockMvc.perform(post("/api/pecas-cortadas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valorTotalCalculado").value(7.72));
        }

        @Test
        @DisplayName("Peça maior que a chapa vira 400 com a mensagem que mostra as medidas")
        void pecaMaiorQueChapaVira400() throws Exception {
            when(pecaCortadaService.criar(any())).thenThrow(new IllegalArgumentException(
                    "A peça de 1500 x 2000 mm não cabe na chapa de 1200 x 3000 mm, nem girada."));

            PecaCortadaRequest request = new PecaCortadaRequest(
                    "Peça grande", new BigDecimal("1200"), new BigDecimal("3000"),
                    new BigDecimal("695.00"), new BigDecimal("1500"), new BigDecimal("2000"),
                    1, 4L, 12L);

            mockMvc.perform(post("/api/pecas-cortadas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem")
                            .value(org.hamcrest.Matchers.containsString("não cabe")));
        }

        @Test
        @DisplayName("Quantidade zero é recusada antes do serviço")
        void quantidadeZeroEhRecusada() throws Exception {
            PecaCortadaRequest invalida = new PecaCortadaRequest(
                    "Flange", new BigDecimal("1200"), new BigDecimal("3000"),
                    new BigDecimal("695.00"), new BigDecimal("200"), new BigDecimal("100"),
                    0, 4L, 12L);

            mockMvc.perform(post("/api/pecas-cortadas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(invalida)).with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(pecaCortadaService, never()).criar(any());
        }

        @Test
        @DisplayName("OS inexistente vira 404")
        void osInexistenteVira404() throws Exception {
            when(pecaCortadaService.listarPorOrdemServico(999L))
                    .thenThrow(new RecursoNaoEncontradoException(
                            "Ordem de Serviço não encontrada com ID: 999"));

            mockMvc.perform(get("/api/ordens-servico/999/pecas-cortadas"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("O filtro por empresa chega ao serviço")
    void filtroPorEmpresaChegaAoServico() throws Exception {
        when(pecaCortadaService.listar(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/pecas-cortadas")
                        .param("empresaId", "2")
                        .param("busca", "flange"))
                .andExpect(status().isOk());

        verify(pecaCortadaService).listar(eq("flange"), eq(2L), any(), any(), any());
    }
}
