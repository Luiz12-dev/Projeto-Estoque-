package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.exception.EstoqueInsuficienteException;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional
    public MovimentacaoResponse registrar(MovimentacaoRequest request) {
        Produto produto = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + request.produtoId()));

        // Validação de estoque para saídas
        if (request.tipo() == TipoMovimentacao.SAIDA) {
            if (produto.getQuantidadeAtual().compareTo(request.quantidade()) < 0) {
                throw new EstoqueInsuficienteException(
                        String.format("Estoque insuficiente para o produto '%s'. Disponível: %s %s, Solicitado: %s %s",
                                produto.getNome(),
                                produto.getQuantidadeAtual().stripTrailingZeros().toPlainString(),
                                produto.getUnidadeMedida(),
                                request.quantidade().stripTrailingZeros().toPlainString(),
                                produto.getUnidadeMedida()
                        )
                );
            }
        }

        // Atualiza quantidade do produto
        BigDecimal novaQuantidade = request.tipo() == TipoMovimentacao.ENTRADA
                ? produto.getQuantidadeAtual().add(request.quantidade())
                : produto.getQuantidadeAtual().subtract(request.quantidade());

        produto.setQuantidadeAtual(novaQuantidade);
        produtoRepository.save(produto);

        // Registra a movimentação
        Usuario usuarioLogado = getUsuarioLogado();

        // Fallback: se valorUnitario não foi informado, usa o valor cadastrado no produto
        BigDecimal valorUnitario = request.valorUnitario();
        if (valorUnitario == null && produto.getValorUnitario() != null) {
            valorUnitario = produto.getValorUnitario();
        }

        Movimentacao movimentacao = Movimentacao.builder()
                .tipo(request.tipo())
                .quantidade(request.quantidade())
                .valorUnitario(valorUnitario)
                .dataHora(LocalDateTime.now())
                .observacao(request.observacao())
                .produto(produto)
                .usuario(usuarioLogado)
                .build();

        movimentacao = movimentacaoRepository.save(movimentacao);
        return MovimentacaoResponse.fromEntity(movimentacao);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarTodas(Pageable pageable) {
        return movimentacaoRepository.findAll(pageable)
                .map(MovimentacaoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarPorProduto(Long produtoId, Pageable pageable) {
        return movimentacaoRepository.findByProdutoIdOrderByDataHoraDesc(produtoId, pageable)
                .map(MovimentacaoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> buscarUltimas() {
        return movimentacaoRepository.findTop5ByOrderByDataHoraDesc().stream()
                .map(MovimentacaoResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarMovimentacoesMes() {
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicio = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fim = mesAtual.atEndOfMonth().atTime(23, 59, 59);
        return movimentacaoRepository.countByDataHoraBetween(inicio, fim);
    }

    private Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
