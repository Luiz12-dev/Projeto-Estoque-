package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;

/**
 * Um bloco da aba "Orçamentos", agrupado por empresa.
 *
 * @param pendentes orçamento parado é dinheiro esperando resposta — é o número
 *                  que decide se alguém precisa ligar para o cliente.
 */
public record ResumoEmpresaOrcamentosResponse(
        Long empresaId,
        String empresa,
        long totalOrcamentos,
        long pendentes,
        BigDecimal valorTotal
) {}
