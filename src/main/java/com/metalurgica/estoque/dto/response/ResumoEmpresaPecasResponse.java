package com.metalurgica.estoque.dto.response;

/**
 * Um bloco da aba "Peças Cortadas", agrupado por empresa.
 * <p>
 * Não traz valor de propósito: o preço da peça é calculado a partir da área que
 * ela ocupa na chapa, e não gravado. Repetir essa fórmula em SQL criaria uma
 * segunda fonte de verdade para dinheiro, que cedo ou tarde divergiria da conta
 * que o sistema mostra na tela.
 */
public record ResumoEmpresaPecasResponse(
        Long empresaId,
        String empresa,
        long totalRegistros,
        long totalPecas
) {}
