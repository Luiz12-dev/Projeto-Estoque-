package com.metalurgica.estoque.dto.request;

import com.metalurgica.estoque.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotNull(message = "O perfil (role) é obrigatório")
        Role role,

        // Opcional: informe apenas para redefinir a senha
        @Size(min = 3, message = "A senha deve ter no mínimo 3 caracteres")
        String senha
) {}
