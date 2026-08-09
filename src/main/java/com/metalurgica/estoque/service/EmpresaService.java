package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Empresa;
import com.metalurgica.estoque.domain.repository.EmpresaRepository;
import com.metalurgica.estoque.dto.request.EmpresaRequest;
import com.metalurgica.estoque.dto.request.EmpresaUpdateRequest;
import com.metalurgica.estoque.dto.response.EmpresaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional
    public EmpresaResponse criar(EmpresaRequest request) {
        Empresa empresa = Empresa.builder()
                .nome(request.nome())
                .cnpj(request.cnpj())
                .telefone(request.telefone())
                .email(request.email())
                .endereco(request.endereco())
                .observacao(request.observacao())
                .build();

        empresa = empresaRepository.save(empresa);
        return EmpresaResponse.fromEntity(empresa);
    }

    @Transactional(readOnly = true)
    public Page<EmpresaResponse> listar(String busca, Pageable pageable) {
        Page<Empresa> page = (busca != null && !busca.isBlank())
                ? empresaRepository.buscar(busca.trim(), pageable)
                : empresaRepository.findAll(pageable);
        return page.map(EmpresaResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public EmpresaResponse buscarPorId(Long id) {
        Empresa empresa = buscarEntidadePorId(id);
        return EmpresaResponse.fromEntity(empresa);
    }

    @Transactional(readOnly = true)
    public Empresa buscarEntidadePorId(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada com ID: " + id));
    }

    @Transactional
    public EmpresaResponse atualizar(Long id, EmpresaUpdateRequest request) {
        Empresa empresa = buscarEntidadePorId(id);

        empresa.setNome(request.nome());
        empresa.setCnpj(request.cnpj());
        empresa.setTelefone(request.telefone());
        empresa.setEmail(request.email());
        empresa.setEndereco(request.endereco());
        empresa.setObservacao(request.observacao());

        empresa = empresaRepository.save(empresa);
        return EmpresaResponse.fromEntity(empresa);
    }
}
