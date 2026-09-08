package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.exception.EstoqueInsuficienteException;
import com.metalurgica.estoque.service.MovimentacaoService;
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

@WebMvcTest(MovimentacaoController.class)
@WithMockUser(roles = "OPERADOR")
class MovimentacaoControllerTest extends TesteDeControlador {

    @MockitoBean
    private MovimentacaoService movimentacaoService;

    private static MovimentacaoResponse saida() {
        return new MovimentacaoResponse(
                100L, TipoMovimentacao.SAIDA,
                new BigDecimal("10"), new BigDecimal("23.40"), new BigDecimal("234.00"),
                LocalDateTime.parse("2026-09-01T14:30:00"),
                "Consumo no portão", "Tubo Redondo 1.1/2", "Leo Fantineli",
                12L, "OS-0003");
    }

    @Nested
    @DisplayName("Formato do JSON")
    class FormatoDoJson {

        @Test
        @DisplayName("A movimentação traz os campos que a tela e o detalhe da OS consomem")
        void camposBatemComOModeloDoFrontend() throws Exception {
            when(movimentacaoService.listarTodas(any()))
                    .thenReturn(new PageImpl<>(List.of(saida()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/movimentacoes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(100))
                    .andExpect(jsonPath("$.content[0].tipo").value("SAIDA"))
                    .andExpect(jsonPath("$.content[0].quantidade").value(10))
                    .andExpect(jsonPath("$.content[0].valorUnitario").value(23.40))
                    .andExpect(jsonPath("$.content[0].valorTotal").value(234.00))
                    .andExpect(jsonPath("$.content[0].dataHora").exists())
                    .andExpect(jsonPath("$.content[0].observacao").value("Consumo no portão"))
                    .andExpect(jsonPath("$.content[0].produtoNome").value("Tubo Redondo 1.1/2"))
                    .andExpect(jsonPath("$.content[0].usuarioNome").value("Leo Fantineli"))
                    // A tela liga a movimentação à OS por estes dois:
                    .andExpect(jsonPath("$.content[0].ordemServicoId").value(12))
                    .andExpect(jsonPath("$.content[0].ordemServicoCodigo").value("OS-0003"));
        }

        @Test
        @DisplayName("O tipo sai como texto, não como número do enum")
        void tipoSaiComoTexto() throws Exception {
            // A tela compara com a string 'ENTRADA'/'SAIDA' para escolher a cor
            // e o sinal. Se virar ordinal, tudo fica cinza e sem sinal.
            MovimentacaoResponse entrada = new MovimentacaoResponse(
                    101L, TipoMovimentacao.ENTRADA, new BigDecimal("5"), new BigDecimal("10.00"),
                    new BigDecimal("50.00"), LocalDateTime.parse("2026-09-01T09:00:00"),
                    null, "Chapa Aço 3mm", "Leo", null, null);
            when(movimentacaoService.listarTodas(any()))
                    .thenReturn(new PageImpl<>(List.of(entrada), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/movimentacoes"))
                    .andExpect(jsonPath("$.content[0].tipo").value("ENTRADA"))
                    .andExpect(jsonPath("$.content[0].ordemServicoId")
                            .value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("Movimentação válida devolve 201")
        void movimentacaoValidaDevolve201() throws Exception {
            when(movimentacaoService.registrar(any())).thenReturn(saida());

            MovimentacaoRequest request = new MovimentacaoRequest(
                    5L, TipoMovimentacao.SAIDA, new BigDecimal("10"),
                    new BigDecimal("23.40"), "Consumo no portão", 12L);

            mockMvc.perform(post("/api/movimentacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valorTotal").value(234.00));
        }

        @Test
        @DisplayName("Quantidade zero ou negativa é recusada antes de chegar ao serviço")
        void quantidadeInvalidaEhRecusada() throws Exception {
            for (String quantidade : new String[] { "0", "-5" }) {
                MovimentacaoRequest invalida = new MovimentacaoRequest(
                        5L, TipoMovimentacao.SAIDA, new BigDecimal(quantidade), null, null, null);

                mockMvc.perform(post("/api/movimentacoes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(invalida)).with(csrf()))
                        .andExpect(status().isBadRequest());
            }
            verify(movimentacaoService, never()).registrar(any());
        }

        @Test
        @DisplayName("Sem produto a requisição é recusada")
        void semProdutoEhRecusada() throws Exception {
            MovimentacaoRequest semProduto = new MovimentacaoRequest(
                    null, TipoMovimentacao.SAIDA, new BigDecimal("1"), null, null, null);

            mockMvc.perform(post("/api/movimentacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(semProduto)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Tradução de erros do domínio")
    class TraducaoDeErros {

        @Test
        @DisplayName("Estoque insuficiente vira 400 com a mensagem que diz o saldo")
        void estoqueInsuficienteVira400() throws Exception {
            // A tela mostra esta mensagem para quem está no balcão. Virar 500
            // apagaria a informação de quanto ainda há disponível.
            when(movimentacaoService.registrar(any())).thenThrow(new EstoqueInsuficienteException(
                    "Estoque insuficiente para o produto 'Chapa Aço 3mm'. Disponível: 2 UN"));

            MovimentacaoRequest request = new MovimentacaoRequest(
                    5L, TipoMovimentacao.SAIDA, new BigDecimal("10"), null, null, null);

            mockMvc.perform(post("/api/movimentacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(request)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem")
                            .value(org.hamcrest.Matchers.containsString("Disponível: 2 UN")));
        }
    }

    @Nested
    @DisplayName("Filtros")
    class Filtros {

        @Test
        @DisplayName("Sem filtro nenhum, usa a listagem simples")
        void semFiltroUsaListagemSimples() throws Exception {
            // O controller escolhe entre dois métodos do serviço conforme haja
            // filtro. Trocar essa escolha faria a busca devolver tudo, ou a
            // listagem devolver vazio — os dois passam despercebidos sem teste.
            when(movimentacaoService.listarTodas(any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/movimentacoes")).andExpect(status().isOk());

            verify(movimentacaoService).listarTodas(any());
            verify(movimentacaoService, never()).buscar(any(), any(), any());
        }

        @Test
        @DisplayName("Com filtro, usa a busca")
        void comFiltroUsaBusca() throws Exception {
            when(movimentacaoService.buscar(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/movimentacoes")
                            .param("busca", "chapa")
                            .param("tipo", "SAIDA"))
                    .andExpect(status().isOk());

            verify(movimentacaoService).buscar(eq("chapa"), eq(TipoMovimentacao.SAIDA), any());
            verify(movimentacaoService, never()).listarTodas(any());
        }

        @Test
        @DisplayName("Tipo de movimentação inexistente vira 400, não 500")
        void tipoInvalidoVira400() throws Exception {
            mockMvc.perform(get("/api/movimentacoes").param("tipo", "DEVOLUCAO"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("O histórico por produto é uma rota própria")
        void historicoPorProduto() throws Exception {
            when(movimentacaoService.listarPorProduto(eq(5L), any()))
                    .thenReturn(new PageImpl<>(List.of(saida()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/movimentacoes/produto/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].produtoNome").value("Tubo Redondo 1.1/2"));
        }
    }
}
