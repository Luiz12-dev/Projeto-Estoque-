package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrdemServicoRequest(
        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        @NotNull(message = "A empresa é obrigatória")
        Long empresaId,

        PrioridadeOrdemServico prioridade,

        String observacao,

        @PositiveOrZero(message = "O valor da mão de obra não pode ser negativo")
        BigDecimal valorMaoDeObra
) {}
