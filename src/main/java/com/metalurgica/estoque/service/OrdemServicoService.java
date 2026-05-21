package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.dto.request.OrdemServicoRequest;
import com.metalurgica.estoque.dto.request.OrdemServicoUpdateRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.OrdemServicoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private final OrdemServicoRepository ordemServicoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    @Transactional
    public OrdemServicoResponse criar(OrdemServicoRequest request) {
        String codigo = gerarProximoCodigo();
        Usuario usuarioLogado = getUsuarioLogado();

        OrdemServico os = OrdemServico.builder()
                .codigo(codigo)
                .descricao(request.descricao())
                .cliente(request.cliente())
                .status(StatusOrdemServico.ABERTA)
                .prioridade(request.prioridade() != null ? request.prioridade() : PrioridadeOrdemServico.MEDIA)
                .observacao(request.observacao())
                .usuario(usuarioLogado)
                .build();

        os = ordemServicoRepository.save(os);
        return OrdemServicoResponse.fromEntitySimple(os);
    }

    @Transactional
    public OrdemServicoResponse atualizar(Long id, OrdemServicoUpdateRequest request) {
        OrdemServico os = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + id));

        if (request.descricao() != null && !request.descricao().isBlank()) {
            os.setDescricao(request.descricao());
        }
        if (request.cliente() != null && !request.cliente().isBlank()) {
            os.setCliente(request.cliente());
        }
        if (request.prioridade() != null) {
            os.setPrioridade(request.prioridade());
        }
        if (request.observacao() != null) {
            os.setObservacao(request.observacao());
        }

        // Transição de status
        if (request.status() != null && request.status() != os.getStatus()) {
            os.setStatus(request.status());

            if (request.status() == StatusOrdemServico.CONCLUIDA || request.status() == StatusOrdemServico.CANCELADA) {
                os.setDataConclusao(LocalDateTime.now());
            } else {
                // Reabriu a OS → limpar data de conclusão
                os.setDataConclusao(null);
            }
        }

        os = ordemServicoRepository.save(os);

        return toResponse(os);
    }

    @Transactional(readOnly = true)
    public OrdemServicoResponse buscarPorId(Long id) {
        OrdemServico os = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + id));

        return toResponse(os);
    }

    @Transactional(readOnly = true)
    public Page<OrdemServicoResponse> listar(String busca, StatusOrdemServico status,
                                              LocalDateTime dataInicio, LocalDateTime dataFim,
                                              Pageable pageable) {
        Page<OrdemServico> page = ordemServicoRepository.buscar(
                busca != null && !busca.isBlank() ? busca.trim() : null,
                status != null ? status.name() : null,
                dataInicio,
                dataFim,
                pageable);

        List<Long> ids = page.getContent().stream().map(OrdemServico::getId).toList();

        if (ids.isEmpty()) {
            return page.map(os -> OrdemServicoResponse.fromEntity(os, BigDecimal.ZERO, 0));
        }

        Map<Long, BigDecimal> custosPorIds = movimentacaoRepository.somarCustosPorOsIds(ids)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));

        Map<Long, Integer> contagemPorIds = movimentacaoRepository.contarPorOsIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));

        return page.map(os -> OrdemServicoResponse.fromEntity(
                os,
                custosPorIds.getOrDefault(os.getId(), BigDecimal.ZERO),
                contagemPorIds.getOrDefault(os.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarMovimentacoes(Long osId, Pageable pageable) {
        // Valida que a OS existe
        ordemServicoRepository.findById(osId)
                .orElseThrow(
                        () -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + osId));

        Page<Long> idsPage = movimentacaoRepository.buscarIdsPorOrdemServicoId(osId, pageable);

        List<Movimentacao> movimentacoes = idsPage.getContent().stream()
                .map(id -> movimentacaoRepository.findById(id).orElse(null)).toList();

        return new PageImpl<>(
                movimentacoes.stream().map(MovimentacaoResponse::fromEntity).toList(),
                pageable,
                idsPage.getTotalElements());
    }

    private String gerarProximoCodigo() {
        int max = ordemServicoRepository.findMaxCodigo();
        return String.format("OS-%04d", max + 1);
    }

    private Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private OrdemServicoResponse toResponse(OrdemServico os) {
        List<Long> ids = List.of(os.getId());

        List<Object[]> custoRows = movimentacaoRepository.somarCustosPorOsIds(ids);
        BigDecimal custoTotal = custoRows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) custoRows.get(0)[1];

        List<Object[]> contRows = movimentacaoRepository.contarPorOsIds(ids);
        int totalMov = contRows.isEmpty() ? 0 : ((Long) contRows.get(0)[1]).intValue();

        return OrdemServicoResponse.fromEntity(os, custoTotal, totalMov);
    }
}
