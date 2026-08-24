package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Produto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoResponse(
        Long id,
        Long version,
        String nome,
        String categoria,
        BigDecimal quantidadeAtual,
        BigDecimal quantidadeMinima,
        String unidadeMedida,
        BigDecimal valorUnitario,
        boolean estoqueBaixo,
        BigDecimal larguraMm,
        BigDecimal comprimentoMm,
        BigDecimal precoMetroCorte,
        /** Indica se o produto está apto a ser usado no módulo de Cortes. */
        boolean chapaParametrizada,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static ProdutoResponse fromEntity(Produto produto) {
        return new ProdutoResponse(
                produto.getId(),
                produto.getVersion(),
                produto.getNome(),
                produto.getCategoria(),
                produto.getQuantidadeAtual(),
                produto.getQuantidadeMinima(),
                produto.getUnidadeMedida(),
                produto.getValorUnitario(),
                produto.isEstoqueBaixo(),
                produto.getLarguraMm(),
                produto.getComprimentoMm(),
                produto.getPrecoMetroCorte(),
                produto.isChapaParametrizada(),
                produto.getCriadoEm(),
                produto.getAtualizadoEm()
        );
    }
}
