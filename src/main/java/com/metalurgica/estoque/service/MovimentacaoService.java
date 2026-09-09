package com.metalurgica.estoque.service;

import com.metalurgica.estoque.config.SecurityUtils;
import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.request.MovimentacaoUpdateRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.ResumoMovimentacaoCategoriaResponse;
import com.metalurgica.estoque.exception.EstoqueInsuficienteException;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final OrdemServicoRepository ordemServicoRepository;

    @Transactional
    public MovimentacaoResponse registrar(MovimentacaoRequest movimentacaoRequest) {

        // Usa lock pessimista para evitar race condition de read-modify-write
        Produto produto = produtoRepository.findByIdForUpdate(movimentacaoRequest.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + movimentacaoRequest.produtoId()));

        if (movimentacaoRequest.tipo() == TipoMovimentacao.ENTRADA) {
            produto.adicionarEstoque(movimentacaoRequest.quantidade());
        } else {
            produto.baixarEstoque(movimentacaoRequest.quantidade());
            if (produto.isEstoqueBaixo()) {
                log.warn("Estoque baixo para o produto '{}' (ID {}): quantidade atual {}",
                        produto.getNome(), produto.getId(),
                        produto.getQuantidadeAtual().stripTrailingZeros().toPlainString());
            }
        }

        produtoRepository.save(produto);

        BigDecimal valorUnitario = definirValorUnitario(movimentacaoRequest.valorUnitario(), produto);

        Movimentacao movimentacao = Movimentacao.builder()
                .tipo(movimentacaoRequest.tipo())
                .quantidade(movimentacaoRequest.quantidade())
                .valorUnitario(valorUnitario)
                .observacao(movimentacaoRequest.observacao())
                .produto(produto)
                .usuario(SecurityUtils.getUsuarioLogado())
                .dataHora(LocalDateTime.now())
                .build();

        vincularOrdemDeServico(movimentacao, movimentacaoRequest.ordemServicoId());

        movimentacao = movimentacaoRepository.save(movimentacao);

        return MovimentacaoResponse.fromEntity(movimentacao);
    }

    @Transactional
    public MovimentacaoResponse atualizar(Long id, MovimentacaoUpdateRequest request) {
        Movimentacao movimentacao = movimentacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentação não encontrada com ID: " + id));

        // Lock pessimista no produto para evitar race condition
        Produto produto = produtoRepository.findByIdForUpdate(movimentacao.getProduto().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));

        // Recalcula estoque se a quantidade mudou
        if (request.quantidade() != null && request.quantidade().compareTo(movimentacao.getQuantidade()) != 0) {
            BigDecimal quantidadeAntiga = movimentacao.getQuantidade();
            BigDecimal quantidadeNova = request.quantidade();
            BigDecimal diferenca = quantidadeNova.subtract(quantidadeAntiga);

            if (movimentacao.getTipo() == TipoMovimentacao.ENTRADA) {
                // Entrada: se aumentou a quantidade, adiciona a diferença; se diminuiu, subtrai
                BigDecimal novoEstoque = produto.getQuantidadeAtual().add(diferenca);
                if (novoEstoque.compareTo(BigDecimal.ZERO) < 0) {
                    throw new EstoqueInsuficienteException(
                            String.format(
                                    "Não é possível reduzir a entrada. O estoque do produto '%s' ficaria negativo.",
                                    produto.getNome()));
                }
                produto.setQuantidadeAtual(novoEstoque);
            } else {
                // Saída: se aumentou a quantidade da saída, subtrai mais; se diminuiu, devolve
                BigDecimal novoEstoque = produto.getQuantidadeAtual().subtract(diferenca);
                if (novoEstoque.compareTo(BigDecimal.ZERO) < 0) {
                    throw new EstoqueInsuficienteException(
                            String.format("Estoque insuficiente para o produto '%s'. Disponível: %s %s",
                                    produto.getNome(),
                                    produto.getQuantidadeAtual().stripTrailingZeros().toPlainString(),
                                    produto.getUnidadeMedida()));
                }
                produto.setQuantidadeAtual(novoEstoque);
            }

            produtoRepository.save(produto);
            movimentacao.setQuantidade(quantidadeNova);
        }

        // Atualiza valor unitário se informado
        if (request.valorUnitario() != null) {
            movimentacao.setValorUnitario(request.valorUnitario());
        }

        // Atualiza observação (permite null/vazio para limpar)
        if (request.observacao() != null) {
            movimentacao.setObservacao(request.observacao());
        }

        movimentacao = movimentacaoRepository.save(movimentacao);
        return MovimentacaoResponse.fromEntity(movimentacao);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarTodas(Pageable pageable) {
        // Usa findAllWithFetch para evitar N+1 queries
        return movimentacaoRepository.findAllWithFetch(pageable)
                .map(MovimentacaoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> buscar(String termo, TipoMovimentacao tipo, Pageable pageable) {
        return movimentacaoRepository.buscar(termo, tipo, pageable)
                .map(MovimentacaoResponse::fromEntity);
    }

    /** Blocos da tela, um por categoria de produto. */
    @Transactional(readOnly = true)
    public List<ResumoMovimentacaoCategoriaResponse> resumoPorCategoria() {
        return movimentacaoRepository.resumoPorCategoria();
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarPorCategoria(Long categoriaId, boolean semCategoria, Pageable pageable) {
        Page<com.metalurgica.estoque.domain.entity.Movimentacao> page = semCategoria
                ? movimentacaoRepository.findSemCategoria(pageable)
                : movimentacaoRepository.findByCategoriaId(categoriaId, pageable);
        return page.map(MovimentacaoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarPorProduto(Long produtoId, Pageable pageable) {
        return movimentacaoRepository.findByProdutoIdOrderByDataHoraDesc(produtoId, pageable)
                .map(MovimentacaoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> buscarUltimas() {
        // Usa query com JOIN FETCH para evitar LazyInitializationException
        return movimentacaoRepository.findTop5WithFetch(PageRequest.of(0, 5)).stream()
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

    private BigDecimal definirValorUnitario(BigDecimal valorInformado, Produto produto) {
        if (valorInformado == null && produto.getValorUnitario() != null) {
            return produto.getValorUnitario();
        }
        return valorInformado;
    }

    private void vincularOrdemDeServico(Movimentacao movimentacao, Long ordemServicoId) {
        if (ordemServicoId == null)
            return;

        OrdemServico os = ordemServicoRepository.findById(ordemServicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada com ID: " + ordemServicoId));

        if (!os.isAbertaOuEmAndamento()) {
            throw new IllegalArgumentException(
                    String.format("Não é possível vincular movimentação à OS '%s' pois ela está '%s'", os.getCodigo(),
                            os.getStatus()));
        }

        movimentacao.setOrdemServico(os);
    }

}
