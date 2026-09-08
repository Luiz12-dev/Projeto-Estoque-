package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.response.DashboardResponse;
import com.metalurgica.estoque.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest extends TesteDeControlador {

    @MockitoBean
    private DashboardService dashboardService;

    private static DashboardResponse painel() {
        return new DashboardResponse(
                42L, 3L, 128L,
                new BigDecimal("15400.00"), new BigDecimal("9820.50"),
                List.of(),
                7L, 21L, 30L,
                new BigDecimal("32150.75"));
    }

    @Test
    @DisplayName("OPERADOR não vê o painel — é visão financeira")
    @WithMockUser(roles = "OPERADOR")
    void operadorNaoVeOPainel() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        verify(dashboardService, never()).getDashboard();
    }

    @Test
    @DisplayName("ESCRITORIO vê — quem cuida da papelada precisa do panorama")
    @WithMockUser(roles = "ESCRITORIO")
    void escritorioVe() throws Exception {
        when(dashboardService.getDashboard()).thenReturn(painel());

        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Todos os números que a tela mostra vêm com os nomes certos")
    @WithMockUser(roles = "ADMIN")
    void camposBatemComOModeloDoFrontend() throws Exception {
        when(dashboardService.getDashboard()).thenReturn(painel());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProdutos").value(42))
                .andExpect(jsonPath("$.produtosAbaixoMinimo").value(3))
                .andExpect(jsonPath("$.movimentacoesMes").value(128))
                .andExpect(jsonPath("$.totalInvestido").value(15400.00))
                .andExpect(jsonPath("$.totalSaidas").value(9820.50))
                .andExpect(jsonPath("$.ultimasMovimentacoes").isArray())
                .andExpect(jsonPath("$.osAbertas").value(7))
                .andExpect(jsonPath("$.osConcluidas").value(21))
                .andExpect(jsonPath("$.osTotal").value(30))
                .andExpect(jsonPath("$.valorTotalEstoque").value(32150.75));
    }

    @Test
    @DisplayName("Sistema vazio devolve zeros, não erro")
    @WithMockUser(roles = "ADMIN")
    void sistemaVazioDevolveZeros() throws Exception {
        // Cenário da primeira subida na oficina: banco recém-criado. O painel
        // precisa abrir mesmo sem nenhum dado, senão a primeira impressão do
        // cliente é uma tela de erro.
        when(dashboardService.getDashboard()).thenReturn(new DashboardResponse(
                0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, List.of(),
                0L, 0L, 0L, BigDecimal.ZERO));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProdutos").value(0))
                .andExpect(jsonPath("$.valorTotalEstoque").value(0))
                .andExpect(jsonPath("$.ultimasMovimentacoes").isEmpty());
    }
}
