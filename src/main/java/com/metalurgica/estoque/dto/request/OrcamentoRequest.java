package com.metalurgica.estoque.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record OrcamentoRequest(
        @NotNull(message = "A empresa é obrigatória")
        Long empresaId,

        @NotNull(message = "A margem é obrigatória")
        @PositiveOrZero(message = "A margem não pode ser negativa")
        BigDecimal margemPercentual,

        String observacao,

        @NotEmpty(message = "O orçamento precisa de pelo menos uma peça")
        @Valid
        List<OrcamentoItemRequest> itens
) {}
