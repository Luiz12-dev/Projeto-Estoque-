package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        BigDecimal valorUnitario,

        // Parâmetros de corte — opcionais, preenchidos apenas quando o produto é chapa.
        @DecimalMin(value = "0", inclusive = false, message = "Largura da chapa deve ser maior que zero")
        BigDecimal larguraMm,

        @DecimalMin(value = "0", inclusive = false, message = "Comprimento da chapa deve ser maior que zero")
        BigDecimal comprimentoMm,

        @DecimalMin(value = "0", message = "Preço por metro de corte não pode ser negativo")
        BigDecimal precoMetroCorte,

        @NotNull(message = "Versão é obrigatória")
        Long version
) {
}
