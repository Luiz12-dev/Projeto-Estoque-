package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import com.metalurgica.estoque.dto.request.UsuarioRequest;
import com.metalurgica.estoque.dto.request.UsuarioUpdateRequest;
import com.metalurgica.estoque.dto.response.UsuarioResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .login(request.login())
                .senha(passwordEncoder.encode(request.senha()))
                .role(request.role())
                .build();

        usuario = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioUpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com ID: " + id));

        usuario.setNome(request.nome());
        usuario.setRole(request.role());
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }

        usuario = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(usuario);
    }
}
