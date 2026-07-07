package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.response.DashboardResponse;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoService movimentacaoService;
    private final OrdemServicoRepository ordemServicoRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long totalProdutos = produtoRepository.count();
        long produtosAbaixoMinimo = produtoRepository.countEstoqueBaixo();
        long movimentacoesMes = movimentacaoService.contarMovimentacoesMes();
        BigDecimal totalInvestido = movimentacaoRepository.somarValorTotalPorTipo(TipoMovimentacao.ENTRADA);
        BigDecimal totalSaidas = movimentacaoRepository.somarValorTotalPorTipo(TipoMovimentacao.SAIDA);
        List<MovimentacaoResponse> ultimasMovimentacoes = movimentacaoService.buscarUltimas();
        BigDecimal valorTotalEstoque = produtoRepository.calcularValorTotalEstoque();

        long osAbertas = ordemServicoRepository.countByStatus(StatusOrdemServico.ABERTA)
                + ordemServicoRepository.countByStatus(StatusOrdemServico.EM_ANDAMENTO);
        long osConcluidas = ordemServicoRepository.countByStatus(StatusOrdemServico.CONCLUIDA);
        long osTotal = ordemServicoRepository.count();

        return new DashboardResponse(
                totalProdutos,
                produtosAbaixoMinimo,
                movimentacoesMes,
                totalInvestido,
                totalSaidas,
                ultimasMovimentacoes,
                osAbertas,
                osConcluidas,
                osTotal,
                valorTotalEstoque
        );
    }
}
