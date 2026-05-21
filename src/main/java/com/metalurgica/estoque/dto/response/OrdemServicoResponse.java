package com.metalurgica.estoque.dto.response;

import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrdemServicoResponse(
        Long id,
        String codigo,
        String descricao,
        String cliente,
        StatusOrdemServico status,
        PrioridadeOrdemServico prioridade,
        LocalDateTime dataAbertura,
        LocalDateTime dataConclusao,
        String observacao,
        BigDecimal custoTotal,
        int totalMovimentacoes,
        String usuarioNome
) {
    public static OrdemServicoResponse fromEntity(OrdemServico os, BigDecimal custoTotal, int totalMovimentacoes) {
        return new OrdemServicoResponse(
                os.getId(),
                os.getCodigo(),
                os.getDescricao(),
                os.getCliente(),
                os.getStatus(),
                os.getPrioridade(),
                os.getDataAbertura(),
                os.getDataConclusao(),
                os.getObservacao(),
                custoTotal != null ? custoTotal : BigDecimal.ZERO,
                totalMovimentacoes,
                os.getUsuario().getNome()
        );
    }

    public static OrdemServicoResponse fromEntitySimple(OrdemServico os) {
        return fromEntity(os, BigDecimal.ZERO, 0);
    }
}
