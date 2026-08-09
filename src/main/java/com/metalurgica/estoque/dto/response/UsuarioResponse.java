package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.Role;

public record UsuarioResponse(
        Long id,
        String nome,
        String login,
        Role role
) {
    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getLogin(),
                usuario.getRole()
        );
    }
}
