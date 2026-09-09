package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        Long categoriaId,

        @NotNull(message = "Quantidade atual é obrigatória")
        @DecimalMin(value = "0", message = "Quantidade atual não pode ser negativa")
        BigDecimal quantidadeAtual,

        @NotNull(message = "Quantidade mínima é obrigatória")
        @DecimalMin(value = "0", message = "Quantidade mínima não pode ser negativa")
        BigDecimal quantidadeMinima,

        @NotBlank(message = "Unidade de medida é obrigatória")
        String unidadeMedida,
        
        @Digits(integer = 8, fraction = 2, message = "Formato de valor financeiro inválido")
        @DecimalMin(value = "0", message = "Valor unitário não pode ser negativo")
        BigDecimal valorUnitario,

        // Parâmetros de corte — opcionais, preenchidos apenas quando o produto é chapa.
        @DecimalMin(value = "0", inclusive = false, message = "Largura da chapa deve ser maior que zero")
        BigDecimal larguraMm,

        @DecimalMin(value = "0", inclusive = false, message = "Comprimento da chapa deve ser maior que zero")
        BigDecimal comprimentoMm,

        @DecimalMin(value = "0", message = "Preço por metro de corte não pode ser negativo")
        BigDecimal precoMetroCorte
) {}
