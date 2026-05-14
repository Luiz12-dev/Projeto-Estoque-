package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MovimentacaoUpdateRequest(
        @Positive(message = "Quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @DecimalMin(value = "0", message = "Valor unitário não pode ser negativo")
        BigDecimal valorUnitario,

        String observacao
) {}
