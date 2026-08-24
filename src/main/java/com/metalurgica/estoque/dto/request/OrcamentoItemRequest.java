package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Uma peça a orçar. Só as medidas da peça são obrigatórias — as dimensões e o
 * preço da chapa vêm do cadastro do produto, não são redigitados aqui.
 * <p>
 * Os campos de corte interno são opcionais: quem corta apenas retângulos não
 * informa nada além das medidas.
 */
public record OrcamentoItemRequest(
        @NotBlank(message = "A descrição da peça é obrigatória")
        String nome,

        @NotNull(message = "A chapa é obrigatória")
        Long produtoId,

        @NotNull(message = "A largura da peça é obrigatória")
        @Positive(message = "A largura da peça deve ser maior que zero")
        BigDecimal larguraPecaMm,

        @NotNull(message = "O comprimento da peça é obrigatório")
        @Positive(message = "O comprimento da peça deve ser maior que zero")
        BigDecimal comprimentoPecaMm,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        Integer quantidade,

        @PositiveOrZero(message = "A quantidade de furos não pode ser negativa")
        Integer quantidadeFuros,

        @PositiveOrZero(message = "O diâmetro do furo não pode ser negativo")
        BigDecimal diametroFuroMm,

        @PositiveOrZero(message = "O corte adicional não pode ser negativo")
        BigDecimal corteExtraMetros
) {}
