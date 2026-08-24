package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Orcamento;
import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrcamentoResponse(
        Long id,
        String codigo,
        Long empresaId,
        String empresaNome,
        SituacaoOrcamento situacao,
        BigDecimal margemPercentual,
        BigDecimal valorTotal,
        String observacao,
        String usuarioNome,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        List<OrcamentoItemResponse> itens
) {
    /** Versão completa, com os itens. Usada no detalhe. */
    public static OrcamentoResponse fromEntity(Orcamento orcamento) {
        return montar(orcamento, orcamento.getItens().stream()
                .map(OrcamentoItemResponse::fromEntity)
                .toList());
    }

    /**
     * Versão para listagem, sem carregar os itens — a lista mostra código,
     * empresa, situação e total, e buscar os itens de cada linha seria N+1 puro.
     */
    public static OrcamentoResponse fromEntitySemItens(Orcamento orcamento) {
        return montar(orcamento, List.of());
    }

    private static OrcamentoResponse montar(Orcamento o, List<OrcamentoItemResponse> itens) {
        return new OrcamentoResponse(
                o.getId(),
                o.getCodigo(),
                o.getEmpresa().getId(),
                o.getEmpresa().getNome(),
                o.getSituacao(),
                o.getMargemPercentual(),
                o.getValorTotal(),
                o.getObservacao(),
                o.getUsuario().getNome(),
                o.getCriadoEm(),
                o.getAtualizadoEm(),
                itens
        );
    }
}
