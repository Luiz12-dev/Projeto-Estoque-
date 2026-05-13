package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import com.metalurgica.estoque.dto.request.LoginRequest;
import com.metalurgica.estoque.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public TokenResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByLogin(request.login())
                .orElseThrow(() -> new IllegalArgumentException("Login ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new IllegalArgumentException("Login ou senha inválidos");
        }

        String token = tokenService.gerarToken(usuario);
        return new TokenResponse(token);
    }
}
