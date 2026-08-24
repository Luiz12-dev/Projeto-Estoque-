package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;

/**
 * Atualização de um orçamento já emitido. Só situação e observação: os itens e
 * os valores ficam congelados, porque o orçamento já foi comunicado ao cliente.
 * Preço diferente pede orçamento novo.
 */
public record OrcamentoUpdateRequest(
        SituacaoOrcamento situacao,
        String observacao
) {}
