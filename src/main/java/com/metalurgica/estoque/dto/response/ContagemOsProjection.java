package com.metalurgica.estoque.dto.response;

/**
 * Projeção DTO para contagem de movimentações por Ordem de Serviço.
 * Substitui o uso frágil de Object[] nas queries do repositório.
 */
public record ContagemOsProjection(Long osId, Long total) {
}
