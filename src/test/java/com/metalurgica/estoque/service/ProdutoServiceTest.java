package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.ProdutoRequest;
import com.metalurgica.estoque.dto.response.ProdutoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @InjectMocks
    private ProdutoService produtoService;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    @BeforeEach
    void setUp() {
        // Inicializa Contexto de Segurança
        Usuario usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Deve criar uma movimentação de ENTRADA automaticamente ao cadastrar um produto com quantidade inicial > 0")
    void deveCriarMovimentacaoDeEntradaAoCriarProdutoComQuantidadeInicial() {
        // Dado
        ProdutoRequest request = new ProdutoRequest(
                "Novo Produto",
                "Categoria Teste",
                new BigDecimal("50.00"), // Quantidade inicial
                new BigDecimal("10.00"),
                "UN",
                new BigDecimal("15.50")
        );

        Produto produtoMockSalvo = Produto.builder()
                .id(99L)
                .nome("Novo Produto")
                .quantidadeAtual(new BigDecimal("50.00"))
                .quantidadeMinima(new BigDecimal("10.00"))
                .unidadeMedida("UN")
                .build();

        // O repository salva o produto e retorna ele com ID
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMockSalvo);

        // Quando
        ProdutoResponse response = produtoService.criar(request);

        // Então
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(99L);

        // Verifica se o ProdutoRepository foi chamado para salvar
        verify(produtoRepository, times(1)).save(any(Produto.class));

        // Verifica se a MovimentacaoRepository foi chamada para criar a entrada
        ArgumentCaptor<Movimentacao> captor = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository, times(1)).save(captor.capture());

        Movimentacao movCapturada = captor.getValue();
        assertThat(movCapturada.getProduto().getId()).isEqualTo(99L);
        assertThat(movCapturada.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(movCapturada.getQuantidade()).isEqualByComparingTo("50.00");
        assertThat(movCapturada.getObservacao()).isEqualTo("Ajuste de Estoque Inicial");
    }

    @Test
    @DisplayName("Não deve criar movimentação se o produto for criado com quantidade zero")
    void naoDeveCriarMovimentacaoAoCriarProdutoComQuantidadeZero() {
        // Dado
        ProdutoRequest request = new ProdutoRequest(
                "Produto Vazio",
                "Categoria",
                BigDecimal.ZERO, // Quantidade zero
                new BigDecimal("5.00"),
                "UN",
                new BigDecimal("0.00") // Não usar null aqui para evitar NPE na formatação
        );

        Produto produtoMockSalvo = Produto.builder()
                .id(100L)
                .nome("Produto Vazio")
                .quantidadeAtual(BigDecimal.ZERO)
                .quantidadeMinima(new BigDecimal("5.00"))
                .build();

        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMockSalvo);

        // Quando
        produtoService.criar(request);

        // Então
        verify(produtoRepository, times(1)).save(any(Produto.class));
        // Garante que a movimentação NUNCA foi chamada
        verify(movimentacaoRepository, never()).save(any(Movimentacao.class));
    }
}
