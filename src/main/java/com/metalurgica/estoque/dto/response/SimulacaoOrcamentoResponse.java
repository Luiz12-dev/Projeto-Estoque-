package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de uma simulação de preço. Mesmo formato do orçamento gravado,
 * sem identidade: nada foi persistido.
 */
public record SimulacaoOrcamentoResponse(
        BigDecimal margemPercentual,
        BigDecimal valorTotal,
        List<OrcamentoItemResponse> itens
) {}
