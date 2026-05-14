package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.ProdutoRequest;
import com.metalurgica.estoque.dto.request.ProdutoUpdateRequest;
import com.metalurgica.estoque.dto.response.ProdutoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Produto produto = Produto.builder()
                .nome(request.nome())
                .categoria(request.categoria())
                .quantidadeAtual(request.quantidadeAtual())
                .quantidadeMinima(request.quantidadeMinima())
                .unidadeMedida(request.unidadeMedida().toUpperCase())
                .valorUnitario(request.valorUnitario())
                .build();

        produto = produtoRepository.save(produto);

        // Regra de negócio: se quantidadeAtual > 0, gerar movimentação de entrada automática
        if (request.quantidadeAtual().compareTo(BigDecimal.ZERO) > 0) {
            Usuario usuarioLogado = getUsuarioLogado();

            Movimentacao movimentacao = Movimentacao.builder()
                    .tipo(TipoMovimentacao.ENTRADA)
                    .quantidade(request.quantidadeAtual())
                    .valorUnitario(request.valorUnitario() != null ? request.valorUnitario() : BigDecimal.ZERO)
                    .dataHora(LocalDateTime.now())
                    .observacao("Ajuste de Estoque Inicial")
                    .produto(produto)
                    .usuario(usuarioLogado)
                    .build();

            movimentacaoRepository.save(movimentacao);
        }

        return ProdutoResponse.fromEntity(produto);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(String busca, Pageable pageable) {
        Page<Produto> page = (busca != null && !busca.isBlank())
                ? produtoRepository.buscar(busca.trim(), pageable)
                : produtoRepository.findAll(pageable);
        return page.map(ProdutoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id));
        return ProdutoResponse.fromEntity(produto);
    }

    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoUpdateRequest request) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id));

        // Atualiza apenas dados cadastrais — quantidadeAtual só muda via movimentação
        produto.setNome(request.nome());
        produto.setCategoria(request.categoria());
        if (request.quantidadeMinima() != null) {
            produto.setQuantidadeMinima(request.quantidadeMinima());
        }
        produto.setUnidadeMedida(request.unidadeMedida().toUpperCase());
        produto.setValorUnitario(request.valorUnitario());

        produto = produtoRepository.save(produto);
        return ProdutoResponse.fromEntity(produto);
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarEstoqueBaixo() {
        return produtoRepository.findEstoqueBaixo().stream()
                .map(ProdutoResponse::fromEntity)
                .toList();
    }

    private Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
