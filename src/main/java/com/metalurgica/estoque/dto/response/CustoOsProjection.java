package com.metalurgica.estoque.dto.response;

import java.math.BigDecimal;

/**
 * Projeção DTO para resultado de soma de custos por Ordem de Serviço.
 * Substitui o uso frágil de Object[] nas queries do repositório.
 */
public record CustoOsProjection(Long osId, BigDecimal custo) {
}
