package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;

/**
 * Resumo de uma categoria para os blocos da tela de Produtos.
 * <p>
 * Os números vêm de uma agregação no banco, e não da página carregada na tela:
 * um bloco que diz "3 itens" porque só três couberam na página seria pior que
 * a listagem corrida que ele substituiu.
 *
 * @param itensAbaixoMinimo é o único número que muda o dia do Leo — é o que
 *                          decide se ele precisa comprar alguma coisa.
 */
public record ResumoCategoriaResponse(
        String categoria,
        long totalItens,
        long itensAbaixoMinimo,
        BigDecimal valorEmEstoque
) {}
