package com.metalurgica.estoque.service;

import com.metalurgica.estoque.config.SecurityUtils;
import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.PecaCortada;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.domain.repository.PecaCortadaRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.request.PecaCortadaRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.PecaCortadaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Registro de uma peça efetivamente cortada. O valor lançado é apenas o
 * rateio do material consumido (CalculadoraCorte.calcularCustoMaterial) — a
 * precificação com corte e margem é do orçamento. O consumo é registrado
 * reaproveitando o fluxo normal de Movimentacao (SAIDA), então a validação
 * de estoque insuficiente e o vínculo com a OS seguem a mesma regra já
 * usada em qualquer outra baixa de material.
 */
@Service
@RequiredArgsConstructor
public class PecaCortadaService {

    private final PecaCortadaRepository pecaCortadaRepository;
    private final ProdutoRepository produtoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoService movimentacaoService;
    private final CalculadoraCorte calculadoraCorte;

    @Transactional
    public PecaCortadaResponse criar(PecaCortadaRequest request) {
        Produto produto = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + request.produtoId()));

        OrdemServico ordemServico = ordemServicoRepository.findById(request.ordemServicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + request.ordemServicoId()));

        // Somente o rateio de material. Sem parcela de corte e sem margem: este valor
        // alimenta uma movimentação de estoque, e tempo de máquina não é estoque.
        // Precificação para o cliente é responsabilidade do orçamento.
        BigDecimal valorUnitarioCalculado = calculadoraCorte.calcularCustoMaterial(
                request.larguraChapaMm(), request.comprimentoChapaMm(), request.valorChapa(),
                request.larguraPecaMm(), request.comprimentoPecaMm());

        Usuario usuarioLogado = SecurityUtils.getUsuarioLogado();

        // Reaproveita o fluxo de Movimentacao para debitar o estoque da chapa
        // com a mesma validação de estoque insuficiente e vínculo de OS.
        MovimentacaoRequest movRequest = new MovimentacaoRequest(
                produto.getId(),
                TipoMovimentacao.SAIDA,
                BigDecimal.valueOf(request.quantidade()),
                valorUnitarioCalculado,
                "Corte a laser: " + request.nome(),
                ordemServico.getId());
        MovimentacaoResponse movResponse = movimentacaoService.registrar(movRequest);
        Movimentacao movimentacao = movimentacaoRepository.getReferenceById(movResponse.id());

        PecaCortada peca = PecaCortada.builder()
                .nome(request.nome())
                .larguraChapaMm(request.larguraChapaMm())
                .comprimentoChapaMm(request.comprimentoChapaMm())
                .valorChapa(request.valorChapa())
                .larguraPecaMm(request.larguraPecaMm())
                .comprimentoPecaMm(request.comprimentoPecaMm())
                .quantidade(request.quantidade())
                .valorUnitarioCalculado(valorUnitarioCalculado)
                .produto(produto)
                .ordemServico(ordemServico)
                .movimentacao(movimentacao)
                .usuario(usuarioLogado)
                .build();

        peca = pecaCortadaRepository.save(peca);
        return PecaCortadaResponse.fromEntity(peca);
    }

    /**
     * Histórico consolidado, para a aba de Cortes. Responde "tudo que já cortei
     * para a empresa X", que a listagem por OS sozinha não conseguia responder.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PecaCortadaResponse> listar(
            String busca, Long empresaId,
            java.time.LocalDateTime dataInicio, java.time.LocalDateTime dataFim,
            org.springframework.data.domain.Pageable pageable) {

        String termo = (busca != null && !busca.isBlank()) ? busca.trim() : null;

        return pecaCortadaRepository.buscar(termo, empresaId, dataInicio, dataFim, pageable)
                .map(PecaCortadaResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<PecaCortadaResponse> listarPorOrdemServico(Long ordemServicoId) {
        ordemServicoRepository.findById(ordemServicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + ordemServicoId));

        return pecaCortadaRepository.findByOrdemServicoId(ordemServicoId).stream()
                .map(PecaCortadaResponse::fromEntity)
                .toList();
    }

}
