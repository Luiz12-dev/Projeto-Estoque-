package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.PecaCortada;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PecaCortadaResponse(
        Long id,
        String nome,
        BigDecimal larguraChapaMm,
        BigDecimal comprimentoChapaMm,
        BigDecimal valorChapa,
        BigDecimal larguraPecaMm,
        BigDecimal comprimentoPecaMm,
        Integer quantidade,
        BigDecimal valorUnitarioCalculado,
        BigDecimal valorTotalCalculado,
        Long produtoId,
        String produtoNome,
        Long ordemServicoId,
        String ordemServicoCodigo,
        Long empresaId,
        String empresaNome,
        Long movimentacaoId,
        String usuarioNome,
        LocalDateTime criadoEm
) {
    public static PecaCortadaResponse fromEntity(PecaCortada p) {
        return new PecaCortadaResponse(
                p.getId(),
                p.getNome(),
                p.getLarguraChapaMm(),
                p.getComprimentoChapaMm(),
                p.getValorChapa(),
                p.getLarguraPecaMm(),
                p.getComprimentoPecaMm(),
                p.getQuantidade(),
                p.getValorUnitarioCalculado(),
                p.getValorUnitarioCalculado().multiply(BigDecimal.valueOf(p.getQuantidade())),
                p.getProduto().getId(),
                p.getProduto().getNome(),
                p.getOrdemServico().getId(),
                p.getOrdemServico().getCodigo(),
                p.getOrdemServico().getEmpresa().getId(),
                p.getOrdemServico().getEmpresa().getNome(),
                p.getMovimentacao() != null ? p.getMovimentacao().getId() : null,
                p.getUsuario().getNome(),
                p.getCriadoEm()
        );
    }
}
