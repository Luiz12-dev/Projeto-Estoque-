package com.metalurgica.estoque.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PecaCortadaRequest(
        @NotBlank(message = "O nome/descrição da peça é obrigatório")
        String nome,

        @NotNull(message = "A largura da chapa é obrigatória")
        @Positive(message = "A largura da chapa deve ser maior que zero")
        BigDecimal larguraChapaMm,

        @NotNull(message = "O comprimento da chapa é obrigatório")
        @Positive(message = "O comprimento da chapa deve ser maior que zero")
        BigDecimal comprimentoChapaMm,

        @NotNull(message = "O valor da chapa é obrigatório")
        @Positive(message = "O valor da chapa deve ser maior que zero")
        BigDecimal valorChapa,

        @NotNull(message = "A largura da peça é obrigatória")
        @Positive(message = "A largura da peça deve ser maior que zero")
        BigDecimal larguraPecaMm,

        @NotNull(message = "O comprimento da peça é obrigatório")
        @Positive(message = "O comprimento da peça deve ser maior que zero")
        BigDecimal comprimentoPecaMm,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        Integer quantidade,

        @NotNull(message = "O produto (chapa) é obrigatório")
        Long produtoId,

        @NotNull(message = "A ordem de serviço é obrigatória")
        Long ordemServicoId
) {}
