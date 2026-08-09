package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.Empresa;

import java.time.LocalDateTime;

public record EmpresaResponse(
        Long id,
        String nome,
        String cnpj,
        String telefone,
        String email,
        String endereco,
        String observacao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static EmpresaResponse fromEntity(Empresa empresa) {
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getNome(),
                empresa.getCnpj(),
                empresa.getTelefone(),
                empresa.getEmail(),
                empresa.getEndereco(),
                empresa.getObservacao(),
                empresa.getCriadoEm(),
                empresa.getAtualizadoEm()
        );
    }
}
