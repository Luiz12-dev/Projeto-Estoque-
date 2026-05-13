package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Produto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoResponse(
        Long id,
        String nome,
        String categoria,
        BigDecimal quantidadeAtual,
        BigDecimal quantidadeMinima,
        String unidadeMedida,
        BigDecimal valorUnitario,
        boolean estoqueBaixo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static ProdutoResponse fromEntity(Produto produto) {
        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getCategoria(),
                produto.getQuantidadeAtual(),
                produto.getQuantidadeMinima(),
                produto.getUnidadeMedida(),
                produto.getValorUnitario(),
                produto.isEstoqueBaixo(),
                produto.getCriadoEm(),
                produto.getAtualizadoEm()
        );
    }
}
