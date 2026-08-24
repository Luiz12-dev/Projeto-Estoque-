package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.OrcamentoItem;

import java.math.BigDecimal;

/**
 * Uma peça orçada, com o detalhamento do preço aberto. As parcelas somam
 * exatamente o subtotal e o total é o unitário vezes a quantidade — a conta
 * fecha linha a linha quando o cliente confere.
 * <p>
 * O {@code id} vem nulo quando o resultado é de uma simulação, que não grava nada.
 */
public record OrcamentoItemResponse(
        Long id,
        String nome,
        Long produtoId,
        String produtoNome,
        BigDecimal larguraPecaMm,
        BigDecimal comprimentoPecaMm,
        Integer quantidade,
        Integer quantidadeFuros,
        BigDecimal diametroFuroMm,
        BigDecimal corteExtraMetros,
        BigDecimal larguraChapaMm,
        BigDecimal comprimentoChapaMm,
        BigDecimal valorChapa,
        BigDecimal precoMetroCorte,
        BigDecimal comprimentoCorteMetros,
        BigDecimal custoMaterialUnitario,
        BigDecimal custoCorteUnitario,
        BigDecimal subtotalUnitario,
        BigDecimal precoUnitario,
        BigDecimal precoTotal
) {
    public static OrcamentoItemResponse fromEntity(OrcamentoItem item) {
        return new OrcamentoItemResponse(
                item.getId(),
                item.getNome(),
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getLarguraPecaMm(),
                item.getComprimentoPecaMm(),
                item.getQuantidade(),
                item.getQuantidadeFuros(),
                item.getDiametroFuroMm(),
                item.getCorteExtraMetros(),
                item.getLarguraChapaMm(),
                item.getComprimentoChapaMm(),
                item.getValorChapa(),
                item.getPrecoMetroCorte(),
                item.getComprimentoCorteMetros(),
                item.getCustoMaterialUnitario(),
                item.getCustoCorteUnitario(),
                item.getCustoMaterialUnitario().add(item.getCustoCorteUnitario()),
                item.getPrecoUnitario(),
                item.getPrecoTotal()
        );
    }
}
