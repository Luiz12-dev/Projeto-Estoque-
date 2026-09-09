package com.metalurgica.estoque.service;

import com.metalurgica.estoque.config.SecurityUtils;
import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.ProdutoRequest;
import com.metalurgica.estoque.dto.request.ProdutoUpdateRequest;
import com.metalurgica.estoque.dto.response.ProdutoResponse;
import com.metalurgica.estoque.domain.entity.Categoria;
import com.metalurgica.estoque.domain.repository.CategoriaRepository;
import com.metalurgica.estoque.dto.response.ResumoCategoriaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Produto produto = Produto.builder()
                .nome(request.nome())
                .categoria(categoriaDe(request.categoriaId()))
                .quantidadeAtual(request.quantidadeAtual())
                .quantidadeMinima(request.quantidadeMinima())
                .unidadeMedida(request.unidadeMedida().toUpperCase())
                .valorUnitario(request.valorUnitario())
                .larguraMm(request.larguraMm())
                .comprimentoMm(request.comprimentoMm())
                .precoMetroCorte(request.precoMetroCorte())
                .build();

        produto = produtoRepository.save(produto);

        // Regra de negócio: se quantidadeAtual > 0, gerar movimentação de entrada
        // automática
        if (request.quantidadeAtual().compareTo(BigDecimal.ZERO) > 0) {
            Usuario usuarioLogado = SecurityUtils.getUsuarioLogado();

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
    /**
     * Quando vem categoria, lista só os itens dela — é o que a tela usa ao
     * abrir um bloco. A busca textual continua atravessando as categorias.
     */
    public Page<ProdutoResponse> listar(String busca, Long categoriaId, boolean semCategoria, Pageable pageable) {
        if (semCategoria) {
            return produtoRepository.findByCategoriaIsNull(pageable).map(ProdutoResponse::fromEntity);
        }
        if (categoriaId != null) {
            return produtoRepository.findByCategoriaId(categoriaId, pageable).map(ProdutoResponse::fromEntity);
        }
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

    /**
     * Atualiza dados cadastrais de um produto.
     * Usa o mesmo lock pessimista que as movimentações (findByIdForUpdate) para
     * garantir consistência e evitar deadlocks entre edição e movimentação concorrente.
     * A verificação de version atua como early-fail: rejeita imediatamente se o
     * cliente está trabalhando com dados desatualizados, sem precisar esperar o commit.
     */
    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoUpdateRequest request) {
        Produto produto = produtoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id));

        if (!produto.getVersion().equals(request.version())) {
            throw new OptimisticLockException(
                    "Versão do produto desatualizada. Atualize a página e tente novamente.");
        }

        // Atualiza apenas dados cadastrais — quantidadeAtual só muda via movimentação
        if (request.nome() != null && !request.nome().isBlank()) {
            produto.setNome(request.nome());
        }
        produto.setCategoria(categoriaDe(request.categoriaId()));
        if (request.quantidadeMinima() != null) {
            produto.setQuantidadeMinima(request.quantidadeMinima());
        }
        if (request.unidadeMedida() != null && !request.unidadeMedida().isBlank()) {
            produto.setUnidadeMedida(request.unidadeMedida().toUpperCase());
        }
        produto.setValorUnitario(request.valorUnitario());

        // Parâmetros de corte: atribuídos direto, inclusive null — limpar os campos
        // é a forma de marcar que um produto deixou de ser usado como chapa.
        produto.setLarguraMm(request.larguraMm());
        produto.setComprimentoMm(request.comprimentoMm());
        produto.setPrecoMetroCorte(request.precoMetroCorte());

        produto = produtoRepository.save(produto);
        return ProdutoResponse.fromEntity(produto);
    }

    /**
     * Resumo dos blocos. O grupo "Sem categoria" só entra quando existe algum
     * item solto: um bloco vazio para uma prateleira que não existe seria
     * ruído na tela.
     */
    @Transactional(readOnly = true)
    public List<ResumoCategoriaResponse> resumoPorCategoria() {
        List<ResumoCategoriaResponse> blocos = new ArrayList<>(produtoRepository.resumoPorCategoria());
        if (produtoRepository.contarSemCategoria() > 0) {
            blocos.add(produtoRepository.resumoSemCategoria());
        }
        return blocos;
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarEstoqueBaixo() {
        return produtoRepository.findEstoqueBaixo().stream()
                .map(ProdutoResponse::fromEntity)
                .toList();
    }

    private Categoria categoriaDe(Long id) {
        if (id == null) return null;
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
    }
}
