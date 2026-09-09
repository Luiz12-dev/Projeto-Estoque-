package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;

/**
 * Um bloco da tela de Movimentações, agrupado pela categoria do produto.
 * <p>
 * Entradas e saídas ficam separadas de propósito: somadas, dariam um número
 * sem significado. O que o dono quer saber ao olhar a prateleira "Chapas" é
 * quanto entrou e quanto saiu, não a diferença entre os dois.
 */
public record ResumoMovimentacaoCategoriaResponse(
        Long categoriaId,
        String categoria,
        long totalMovimentacoes,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas
) {}
