package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import jakarta.validation.constraints.NotBlank;

public record OrdemServicoRequest(
        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotBlank(message = "Cliente é obrigatório")
        String cliente,

        PrioridadeOrdemServico prioridade,

        String observacao
) {}
