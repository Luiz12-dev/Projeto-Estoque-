package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.response.DashboardResponse;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.service.MovimentacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoService movimentacaoService;
    private final OrdemServicoRepository ordemServicoRepository;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        long totalProdutos = produtoRepository.count();
        long produtosAbaixoMinimo = produtoRepository.countEstoqueBaixo();
        long movimentacoesMes = movimentacaoService.contarMovimentacoesMes();
        BigDecimal totalInvestido = movimentacaoRepository.somarValorTotalPorTipo(TipoMovimentacao.ENTRADA);
        BigDecimal totalSaidas = movimentacaoRepository.somarValorTotalPorTipo(TipoMovimentacao.SAIDA);
        List<MovimentacaoResponse> ultimasMovimentacoes = movimentacaoService.buscarUltimas();

        long osAbertas = ordemServicoRepository.countByStatus(StatusOrdemServico.ABERTA)
                + ordemServicoRepository.countByStatus(StatusOrdemServico.EM_ANDAMENTO);
        long osConcluidas = ordemServicoRepository.countByStatus(StatusOrdemServico.CONCLUIDA);
        long osTotal = ordemServicoRepository.count();

        var response = new DashboardResponse(
                totalProdutos,
                produtosAbaixoMinimo,
                movimentacoesMes,
                totalInvestido,
                totalSaidas,
                ultimasMovimentacoes,
                osAbertas,
                osConcluidas,
                osTotal
        );

        return ResponseEntity.ok(response);
    }
}

