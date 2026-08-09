package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrdemServicoUpdateRequest(
        String descricao,
        Long empresaId,
        StatusOrdemServico status,
        PrioridadeOrdemServico prioridade,
        String observacao,
        @PositiveOrZero(message = "O valor da mão de obra não pode ser negativo")
        BigDecimal valorMaoDeObra
) {}
