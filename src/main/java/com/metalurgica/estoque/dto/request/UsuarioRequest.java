package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Login é obrigatório")
        @Size(min = 3, max = 50, message = "O login deve ter entre 3 e 50 caracteres")
        String login,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 3, message = "A senha deve ter no mínimo 3 caracteres")
        String senha,

        @NotNull(message = "O perfil (role) é obrigatório")
        Role role
) {}
