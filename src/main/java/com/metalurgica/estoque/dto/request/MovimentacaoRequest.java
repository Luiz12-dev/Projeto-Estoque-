package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MovimentacaoRequest(
        @NotNull(message = "ID do produto é obrigatório")
        Long produtoId,

        @NotNull(message = "Tipo de movimentação é obrigatório")
        TipoMovimentacao tipo,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @DecimalMin(value = "0", message = "Valor unitário não pode ser negativo")
        BigDecimal valorUnitario,

        String observacao,

        Long ordemServicoId
) {}
