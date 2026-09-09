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
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do ProdutoService.
 * Padrão AAA (Arrange-Act-Assert), @Nested para agrupamento semântico.
 */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @InjectMocks
    private ProdutoService produtoService;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    private Usuario usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================
    // Factory Methods — DRY
    // ============================

    private Produto criarProdutoMock(Long id, String nome, String qtdAtual) {
        return Produto.builder()
                .id(id)
                .version(0L)
                .nome(nome)
                .quantidadeAtual(new BigDecimal(qtdAtual))
                .quantidadeMinima(new BigDecimal("5.00"))
                .unidadeMedida("UN")
                .build();
    }

    // ============================
    // CRIAR
    // ============================

    @Nested
    @DisplayName("criar()")
    class Criar {

        @Test
        @DisplayName("Deve criar produto e gerar movimentação ENTRADA quando quantidade inicial > 0")
        void deveCriarComMovimentacaoDeEntrada() {
            // Arrange
            ProdutoRequest request = new ProdutoRequest(
                    "Novo Produto", "Categoria", new BigDecimal("50.00"),
                    new BigDecimal("10.00"), "UN", new BigDecimal("15.50"), null, null, null);

            Produto produtoSalvo = criarProdutoMock(99L, "Novo Produto", "50.00");
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvo);

            // Act
            ProdutoResponse response = produtoService.criar(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(99L);

            ArgumentCaptor<Movimentacao> captor = ArgumentCaptor.forClass(Movimentacao.class);
            verify(movimentacaoRepository, times(1)).save(captor.capture());

            Movimentacao movCriada = captor.getValue();
            assertThat(movCriada.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
            assertThat(movCriada.getQuantidade()).isEqualByComparingTo("50.00");
            assertThat(movCriada.getObservacao()).isEqualTo("Ajuste de Estoque Inicial");
            assertThat(movCriada.getUsuario().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Não deve criar movimentação quando quantidade inicial é zero")
        void naoDeveCriarMovimentacaoComQuantidadeZero() {
            // Arrange
            ProdutoRequest request = new ProdutoRequest(
                    "Produto Vazio", "Categoria", BigDecimal.ZERO,
                    new BigDecimal("5.00"), "UN", new BigDecimal("0.00"), null, null, null);

            Produto produtoSalvo = criarProdutoMock(100L, "Produto Vazio", "0");
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvo);

            // Act
            produtoService.criar(request);

            // Assert
            verify(produtoRepository, times(1)).save(any(Produto.class));
            verify(movimentacaoRepository, never()).save(any(Movimentacao.class));
        }

        @Test
        @DisplayName("Deve converter unidade de medida para uppercase")
        void deveConverterUnidadeMedidaParaUppercase() {
            // Arrange
            ProdutoRequest request = new ProdutoRequest(
                    "Produto", null, BigDecimal.ZERO,
                    BigDecimal.ZERO, "kg", null, null, null, null);

            when(produtoRepository.save(any(Produto.class))).thenAnswer(i -> {
                Produto p = i.getArgument(0);
                p.setId(1L);
                return p;
            });

            // Act
            produtoService.criar(request);

            // Assert
            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository).save(captor.capture());
            assertThat(captor.getValue().getUnidadeMedida()).isEqualTo("KG");
        }

        @Test
        @DisplayName("Deve usar BigDecimal.ZERO quando valorUnitario é null na movimentação automática")
        void deveUsarZeroQuandoValorUnitarioNull() {
            // Arrange
            ProdutoRequest request = new ProdutoRequest(
                    "Produto", null, new BigDecimal("10.00"),
                    BigDecimal.ZERO, "UN", null, null, null, null); // valorUnitario = null

            Produto produtoSalvo = criarProdutoMock(1L, "Produto", "10.00");
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvo);

            // Act
            produtoService.criar(request);

            // Assert
            ArgumentCaptor<Movimentacao> captor = ArgumentCaptor.forClass(Movimentacao.class);
            verify(movimentacaoRepository).save(captor.capture());
            assertThat(captor.getValue().getValorUnitario()).isEqualByComparingTo("0");
        }
    }

    // ============================
    // ATUALIZAR
    // ============================

    @Nested
    @DisplayName("atualizar()")
    class Atualizar {

        @Test
        @DisplayName("Deve atualizar campos cadastrais com version correta")
        void deveAtualizarComVersionCorreta() {
            // Arrange
            Produto produto = criarProdutoMock(1L, "Antigo", "10.00");
            when(produtoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(produto));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produto);

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "Novo Nome", "Nova Categoria", new BigDecimal("3.00"), "KG", null, null, null, null, 0L);

            // Act
            ProdutoResponse response = produtoService.atualizar(1L, request);

            // Assert
            assertThat(produto.getNome()).isEqualTo("Novo Nome");
            assertThat(produto.getCategoria()).isEqualTo("Nova Categoria");
            assertThat(produto.getQuantidadeMinima()).isEqualByComparingTo("3.00");
            assertThat(produto.getUnidadeMedida()).isEqualTo("KG");
            verify(produtoRepository).findByIdForUpdate(1L);
            verify(produtoRepository).save(produto);
        }

        @Test
        @DisplayName("Deve lançar OptimisticLockException quando version está desatualizada")
        void deveLancarExcecaoQuandoVersionDesatualizada() {
            // Arrange
            Produto produto = criarProdutoMock(1L, "Produto", "10.00");
            produto.setVersion(5L); // version no banco = 5
            when(produtoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(produto));

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "Nome", null, null, "UN", null, null, null, null, 3L); // version do cliente = 3

            // Act & Assert
            assertThatThrownBy(() -> produtoService.atualizar(1L, request))
                    .isInstanceOf(OptimisticLockException.class)
                    .hasMessageContaining("desatualizada");

            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar RecursoNaoEncontradoException quando produto não existe")
        void deveLancarExcecaoQuandoProdutoNaoExiste() {
            // Arrange
            when(produtoRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "Nome", null, null, "UN", null, null, null, null, 0L);

            // Act & Assert
            assertThatThrownBy(() -> produtoService.atualizar(999L, request))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        @DisplayName("Deve ignorar nome blank e manter o existente")
        void deveIgnorarNomeBlank() {
            // Arrange
            Produto produto = criarProdutoMock(1L, "Nome Original", "10.00");
            when(produtoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(produto));
            when(produtoRepository.save(any())).thenReturn(produto);

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "   ", null, null, "UN", null, null, null, null, 0L);

            // Act
            produtoService.atualizar(1L, request);

            // Assert
            assertThat(produto.getNome()).isEqualTo("Nome Original");
        }

        @Test
        @DisplayName("Não deve alterar quantidadeAtual — isso só acontece via movimentação")
        void naoDeveAlterarQuantidadeAtual() {
            // Arrange
            Produto produto = criarProdutoMock(1L, "Produto", "50.00");
            when(produtoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(produto));
            when(produtoRepository.save(any())).thenReturn(produto);

            ProdutoUpdateRequest request = new ProdutoUpdateRequest(
                    "Produto", null, null, "UN", null, null, null, null, 0L);

            // Act
            produtoService.atualizar(1L, request);

            // Assert — quantidade não mudou
            assertThat(produto.getQuantidadeAtual()).isEqualByComparingTo("50.00");
        }
    }

    // ============================
    // BUSCAR POR ID
    // ============================

    @Nested
    @DisplayName("buscarPorId()")
    class BuscarPorId {

        @Test
        @DisplayName("Deve retornar ProdutoResponse quando produto existe")
        void deveRetornarProdutoQuandoExiste() {
            // Arrange
            Produto produto = criarProdutoMock(1L, "Produto Existente", "10.00");
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            // Act
            ProdutoResponse response = produtoService.buscarPorId(1L);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.nome()).isEqualTo("Produto Existente");
        }

        @Test
        @DisplayName("Deve lançar RecursoNaoEncontradoException quando produto não existe")
        void deveLancarExcecaoQuandoNaoExiste() {
            // Arrange
            when(produtoRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> produtoService.buscarPorId(999L))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("999");
        }
    }

    // ============================
    // LISTAR
    // ============================

    @Nested
    @DisplayName("listar()")
    class Listar {

        @Test
        @DisplayName("Deve buscar todos quando busca é null")
        void deveListarTodosQuandoBuscaNull() {
            // Arrange
            Page<Produto> page = new PageImpl<>(List.of(criarProdutoMock(1L, "A", "10")));
            when(produtoRepository.findAll(any(PageRequest.class))).thenReturn(page);

            // Act
            Page<ProdutoResponse> response = produtoService.listar(null, null, PageRequest.of(0, 20));

            // Assert
            assertThat(response.getContent()).hasSize(1);
            verify(produtoRepository).findAll(any(PageRequest.class));
            verify(produtoRepository, never()).buscar(any(), any());
        }

        @Test
        @DisplayName("Deve usar buscar() quando termo de busca é informado")
        void deveUsarBuscarQuandoTermoInformado() {
            // Arrange
            Page<Produto> page = new PageImpl<>(List.of());
            when(produtoRepository.buscar(eq("chapa"), any())).thenReturn(page);

            // Act
            produtoService.listar("chapa", null, PageRequest.of(0, 20));

            // Assert
            verify(produtoRepository).buscar(eq("chapa"), any());
            verify(produtoRepository, never()).findAll(any(PageRequest.class));
        }

        @Test
        @DisplayName("Deve usar findAll quando busca é string vazia ou em branco")
        void deveUsarFindAllQuandoBuscaVazia() {
            // Arrange
            Page<Produto> page = new PageImpl<>(List.of());
            when(produtoRepository.findAll(any(PageRequest.class))).thenReturn(page);

            // Act
            produtoService.listar("   ", null, PageRequest.of(0, 20));

            // Assert
            verify(produtoRepository).findAll(any(PageRequest.class));
        }
    }

    // ============================
    // ESTOQUE BAIXO
    // ============================

    @Nested
    @DisplayName("listarEstoqueBaixo()")
    class ListarEstoqueBaixo {

        @Test
        @DisplayName("Deve retornar lista de produtos abaixo do estoque mínimo")
        void deveRetornarProdutosComEstoqueBaixo() {
            // Arrange
            Produto p = criarProdutoMock(1L, "Chapa", "2.00");
            p.setQuantidadeMinima(new BigDecimal("10.00")); // abaixo do mínimo
            when(produtoRepository.findEstoqueBaixo()).thenReturn(List.of(p));

            // Act
            List<ProdutoResponse> resultado = produtoService.listarEstoqueBaixo();

            // Assert
            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).estoqueBaixo()).isTrue();
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando nenhum produto está abaixo do mínimo")
        void deveRetornarListaVazia() {
            // Arrange
            when(produtoRepository.findEstoqueBaixo()).thenReturn(List.of());

            // Act
            List<ProdutoResponse> resultado = produtoService.listarEstoqueBaixo();

            // Assert
            assertThat(resultado).isEmpty();
        }
    }
}
