package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Categoria;

public record CategoriaResponse(Long id, String nome) {

    public static CategoriaResponse fromEntity(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome());
    }
}
