package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.request.MovimentacaoUpdateRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.exception.EstoqueInsuficienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimentacaoServiceTest {

    @InjectMocks
    private MovimentacaoService movimentacaoService;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private Usuario usuarioLogado;
    private Produto produtoMock;

    @BeforeEach
    void setUp() {
        // Configura o usuário logado no SecurityContext (usado pelo MovimentacaoService)
        usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);

        produtoMock = Produto.builder()
                .id(1L)
                .nome("Chapa Aço")
                .quantidadeAtual(new BigDecimal("10.00"))
                .unidadeMedida("UN")
                .build();
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar registrar SAIDA com quantidade maior que o estoque")
    void deveLancarExcecaoAoRegistrarSaidaMaiorQueEstoque() {
        // Dado
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("15.00"), null, "Venda"
        );
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));

        // Quando / Então
        assertThrows(EstoqueInsuficienteException.class, () -> {
            movimentacaoService.registrar(request);
        });

        // Verifica que NADA foi salvo no banco
        verify(produtoRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recalcular o estoque corretamente ao registrar uma ENTRADA")
    void deveRecalcularEstoqueAoRegistrarEntrada() {
        // Dado
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.ENTRADA, new BigDecimal("5.00"), new BigDecimal("10.00"), "Compra"
        );
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(invocation -> {
            Movimentacao m = invocation.getArgument(0);
            m.setId(100L); // simula a geração do ID pelo banco
            return m;
        });

        // Quando
        MovimentacaoResponse response = movimentacaoService.registrar(request);

        // Então
        assertThat(response).isNotNull();
        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("15.00");
        verify(produtoRepository, times(1)).save(produtoMock);
        verify(movimentacaoRepository, times(1)).save(any(Movimentacao.class));
    }

    @Test
    @DisplayName("Deve recalcular o estoque corretamente ao editar a quantidade de uma SAIDA")
    void deveRecalcularEstoqueAoAtualizarQuantidadeDaMovimentacao() {
        // Dado
        // Produto tinha 15 no estoque. A movimentação original (SAIDA) tirou 10. Sobrou 5.
        // Vamos simular que o estoque ATUAL do produto é 5.
        produtoMock.setQuantidadeAtual(new BigDecimal("5.00"));

        Movimentacao movimentacaoExistente = Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.SAIDA)
                .quantidade(new BigDecimal("10.00")) // Saída original foi 10
                .produto(produtoMock)
                .usuario(usuarioLogado)
                .build();

        // O usuário edita a saída informando que na verdade foi uma saída de apenas 3 (ou seja, deve devolver 7)
        MovimentacaoUpdateRequest request = new MovimentacaoUpdateRequest(
                new BigDecimal("3.00"), null, null
        );

        when(movimentacaoRepository.findById(1L)).thenReturn(Optional.of(movimentacaoExistente));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenReturn(movimentacaoExistente);

        // Quando
        movimentacaoService.atualizar(1L, request);

        // Então
        // Se a saída diminuiu de 10 para 3, significa que 7 devem retornar ao estoque. (5 + 7 = 12)
        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("12.00");
        assertThat(movimentacaoExistente.getQuantidade()).isEqualByComparingTo("3.00");
        verify(produtoRepository, times(1)).save(produtoMock);
    }

    @Test
    @DisplayName("Deve lançar exceção ao editar quantidade de uma SAIDA para um valor maior que o estoque atual permite")
    void deveLancarExcecaoAoAtualizarSaidaMaiorQueEstoque() {
        // Dado
        // Estoque atual é 5.
        produtoMock.setQuantidadeAtual(new BigDecimal("5.00"));

        Movimentacao movimentacaoExistente = Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.SAIDA)
                .quantidade(new BigDecimal("10.00"))
                .produto(produtoMock)
                .build();

        // Usuário tenta editar a saída de 10 para 20 (precisaria tirar mais 10 do estoque, mas só tem 5)
        MovimentacaoUpdateRequest request = new MovimentacaoUpdateRequest(
                new BigDecimal("20.00"), null, null
        );

        when(movimentacaoRepository.findById(1L)).thenReturn(Optional.of(movimentacaoExistente));

        // Quando / Então
        assertThrows(EstoqueInsuficienteException.class, () -> {
            movimentacaoService.atualizar(1L, request);
        });

        // Garante que não salvou a edição nem mexeu no produto
        verify(produtoRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }
}
