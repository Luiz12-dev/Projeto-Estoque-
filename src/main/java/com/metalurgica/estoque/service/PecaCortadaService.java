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
import java.math.RoundingMode;
import java.util.List;

/**
 * Calculadora de corte a laser: dado o valor e as dimensões da chapa de
 * origem, calcula o valor de uma peça cortada proporcionalmente à área que
 * ela ocupa na chapa. O consumo de material é registrado no estoque
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

    @Transactional
    public PecaCortadaResponse criar(PecaCortadaRequest request) {
        Produto produto = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + request.produtoId()));

        OrdemServico ordemServico = ordemServicoRepository.findById(request.ordemServicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + request.ordemServicoId()));

        BigDecimal valorUnitarioCalculado = calcularValorProporcional(
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

    @Transactional(readOnly = true)
    public List<PecaCortadaResponse> listarPorOrdemServico(Long ordemServicoId) {
        ordemServicoRepository.findById(ordemServicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada com ID: " + ordemServicoId));

        return pecaCortadaRepository.findByOrdemServicoId(ordemServicoId).stream()
                .map(PecaCortadaResponse::fromEntity)
                .toList();
    }

    /**
     * Rateio proporcional: o valor da peça é a fração da área da chapa que
     * ela ocupa, multiplicada pelo valor total da chapa.
     */
    private BigDecimal calcularValorProporcional(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal valorChapa, BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm) {
        BigDecimal areaChapa = larguraChapaMm.multiply(comprimentoChapaMm);
        BigDecimal areaPeca = larguraPecaMm.multiply(comprimentoPecaMm);

        if (areaPeca.compareTo(areaChapa) > 0) {
            throw new IllegalArgumentException("As dimensões da peça não podem ser maiores que as da chapa.");
        }

        return valorChapa.multiply(areaPeca).divide(areaChapa, 2, RoundingMode.HALF_UP);
    }
}
