package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String categoria,

        @NotNull(message = "Quantidade atual é obrigatória")
        @DecimalMin(value = "0", message = "Quantidade atual não pode ser negativa")
        BigDecimal quantidadeAtual,

        @NotNull(message = "Quantidade mínima é obrigatória")
        @DecimalMin(value = "0", message = "Quantidade mínima não pode ser negativa")
        BigDecimal quantidadeMinima,

        @NotBlank(message = "Unidade de medida é obrigatória")
        String unidadeMedida,

        @DecimalMin(value = "0", message = "Valor unitário não pode ser negativo")
        BigDecimal valorUnitario
) {}
