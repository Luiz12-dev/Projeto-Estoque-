package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long totalProdutos,
        long produtosAbaixoMinimo,
        long movimentacoesMes,
        BigDecimal totalInvestido,
        BigDecimal totalSaidas,
        List<MovimentacaoResponse> ultimasMovimentacoes
) {}
