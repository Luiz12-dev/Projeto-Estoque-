package com.metalurgica.estoque.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simulação de preço sem gravar nada. Não exige empresa: serve para responder
 * "quanto ficaria?" antes de existir um cliente definido, e como contrato
 * verificável contra o cálculo que o frontend faz para o preview instantâneo.
 */
public record SimulacaoOrcamentoRequest(
        @NotNull(message = "A margem é obrigatória")
        @PositiveOrZero(message = "A margem não pode ser negativa")
        BigDecimal margemPercentual,

        @NotEmpty(message = "Informe pelo menos uma peça")
        @Valid
        List<OrcamentoItemRequest> itens
) {}
