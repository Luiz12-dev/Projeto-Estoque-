package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;

public record OrdemServicoUpdateRequest(
        String descricao,
        String cliente,
        StatusOrdemServico status,
        PrioridadeOrdemServico prioridade,
        String observacao
) {}
