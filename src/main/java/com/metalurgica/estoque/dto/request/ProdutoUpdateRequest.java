package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ProdutoUpdateRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String categoria,

        @DecimalMin(value = "0", message = "Quantidade mínima não pode ser negativa")
        BigDecimal quantidadeMinima,

        @NotBlank(message = "Unidade de medida é obrigatória")
        String unidadeMedida,

        @DecimalMin(value = "0", message = "Valor unitário não pode ser negativo")
        BigDecimal valorUnitario
) {}
