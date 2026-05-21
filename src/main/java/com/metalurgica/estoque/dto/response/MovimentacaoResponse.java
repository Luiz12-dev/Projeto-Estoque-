package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoResponse(
        Long id,
        TipoMovimentacao tipo,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        LocalDateTime dataHora,
        String observacao,
        String produtoNome,
        String usuarioNome,
        Long ordemServicoId,
        String ordemServicoCodigo
) {
    public static MovimentacaoResponse fromEntity(Movimentacao mov) {
        BigDecimal valUnit = mov.getValorUnitario() != null ? mov.getValorUnitario() : BigDecimal.ZERO;
        BigDecimal valTotal = mov.getQuantidade().multiply(valUnit);

        return new MovimentacaoResponse(
                mov.getId(),
                mov.getTipo(),
                mov.getQuantidade(),
                mov.getValorUnitario(),
                valTotal,
                mov.getDataHora(),
                mov.getObservacao(),
                mov.getProduto().getNome(),
                mov.getUsuario().getNome(),
                mov.getOrdemServico() != null ? mov.getOrdemServico().getId() : null,
                mov.getOrdemServico() != null ? mov.getOrdemServico().getCodigo() : null
        );
    }
}
