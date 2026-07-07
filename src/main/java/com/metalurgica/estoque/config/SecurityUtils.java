package com.metalurgica.estoque.config;

import com.metalurgica.estoque.domain.entity.Usuario;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitário centralizado para operações de segurança,
 * eliminando duplicação de getUsuarioLogado() em múltiplos services.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    public static Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
