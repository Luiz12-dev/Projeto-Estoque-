package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
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

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    private Usuario usuarioLogado;
    private Produto produtoMock;

    @BeforeEach
    void setUp() {
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
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("15.00"), null, "Venda", null);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));

        assertThrows(EstoqueInsuficienteException.class, () -> {
            movimentacaoService.registrar(request);
        });

        verify(produtoRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recalcular o estoque corretamente ao registrar uma ENTRADA")
    void deveRecalcularEstoqueAoRegistrarEntrada() {
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.ENTRADA, new BigDecimal("5.00"), new BigDecimal("10.00"), "Compra", null);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(invocation -> {
            Movimentacao m = invocation.getArgument(0);
            m.setId(100L);
            return m;
        });

        MovimentacaoResponse response = movimentacaoService.registrar(request);

        assertThat(response).isNotNull();
        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("15.00");
        verify(produtoRepository, times(1)).save(produtoMock);
        verify(movimentacaoRepository, times(1)).save(any(Movimentacao.class));
    }

    @Test
    @DisplayName("Deve recalcular o estoque corretamente ao editar a quantidade de uma SAIDA")
    void deveRecalcularEstoqueAoAtualizarQuantidadeDaMovimentacao() {
        produtoMock.setQuantidadeAtual(new BigDecimal("5.00"));

        Movimentacao movimentacaoExistente = Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.SAIDA)
                .quantidade(new BigDecimal("10.00"))
                .produto(produtoMock)
                .usuario(usuarioLogado)
                .build();

        MovimentacaoUpdateRequest request = new MovimentacaoUpdateRequest(
                new BigDecimal("3.00"), null, null);

        when(movimentacaoRepository.findById(1L)).thenReturn(Optional.of(movimentacaoExistente));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenReturn(movimentacaoExistente);

        movimentacaoService.atualizar(1L, request);

        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("12.00");
        assertThat(movimentacaoExistente.getQuantidade()).isEqualByComparingTo("3.00");
        verify(produtoRepository, times(1)).save(produtoMock);
    }

    @Test
    @DisplayName("Deve lançar exceção ao editar quantidade de uma SAIDA para um valor maior que o estoque atual permite")
    void deveLancarExcecaoAoAtualizarSaidaMaiorQueEstoque() {
        produtoMock.setQuantidadeAtual(new BigDecimal("5.00"));

        Movimentacao movimentacaoExistente = Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.SAIDA)
                .quantidade(new BigDecimal("10.00"))
                .produto(produtoMock)
                .build();

        MovimentacaoUpdateRequest request = new MovimentacaoUpdateRequest(
                new BigDecimal("20.00"), null, null);

        when(movimentacaoRepository.findById(1L)).thenReturn(Optional.of(movimentacaoExistente));

        assertThrows(EstoqueInsuficienteException.class, () -> {
            movimentacaoService.atualizar(1L, request);
        });

        verify(produtoRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve registrar SAIDA com sucesso e recalcular estoque")
    void deveRegistrarSaidaComSucesso() {
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("5.00"), null, "Uso interno", null);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(invocation -> {
            Movimentacao m = invocation.getArgument(0);
            m.setId(101L);
            return m;
        });

        MovimentacaoResponse response = movimentacaoService.registrar(request);

        assertThat(response).isNotNull();
        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("5.00");
        verify(produtoRepository, times(1)).save(produtoMock);
    }

    @Test
    @DisplayName("Deve registrar movimentação vinculada a uma Ordem de Serviço")
    void deveRegistrarComOrdemDeServico() {
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("2.00"), null, "Uso OS", 10L);

        OrdemServico os = new OrdemServico();
        os.setId(10L);
        os.setStatus(StatusOrdemServico.EM_ANDAMENTO);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(i -> {
            Movimentacao m = i.getArgument(0);
            m.setId(102L);
            return m;
        });

        MovimentacaoResponse response = movimentacaoService.registrar(request);

        assertThat(response.ordemServicoId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar vincular a uma OS fechada")
    void deveLancarExcecaoAoVincularOsFechada() {
        MovimentacaoRequest request = new MovimentacaoRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("2.00"), null, "Uso OS", 10L);

        OrdemServico os = new OrdemServico();
        os.setId(10L);
        os.setCodigo("OS-001");
        os.setStatus(StatusOrdemServico.CONCLUIDA);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoMock));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));

        assertThrows(IllegalArgumentException.class, () -> {
            movimentacaoService.registrar(request);
        });
    }

    @Test
    @DisplayName("Deve recalcular o estoque corretamente ao editar a quantidade de uma ENTRADA (diminuindo)")
    void deveRecalcularEstoqueAoAtualizarEntrada() {
        produtoMock.setQuantidadeAtual(new BigDecimal("15.00"));

        Movimentacao movimentacaoExistente = Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.ENTRADA)
                .quantidade(new BigDecimal("5.00"))
                .produto(produtoMock)
                .usuario(usuarioLogado)
                .build();

        MovimentacaoUpdateRequest request = new MovimentacaoUpdateRequest(
                new BigDecimal("3.00"), null, null);

        when(movimentacaoRepository.findById(1L)).thenReturn(Optional.of(movimentacaoExistente));
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenReturn(movimentacaoExistente);

        movimentacaoService.atualizar(1L, request);

        assertThat(produtoMock.getQuantidadeAtual()).isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("Deve listar todas as movimentações")
    void deveListarTodas() {
        Movimentacao m = new Movimentacao();
        m.setProduto(produtoMock);
        m.setQuantidade(new BigDecimal("5.00"));
        m.setValorUnitario(new BigDecimal("10.00"));
        m.setUsuario(usuarioLogado);
        Page<Movimentacao> page = new PageImpl<>(List.of(m));

        when(movimentacaoRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<MovimentacaoResponse> response = movimentacaoService.listarTodas(PageRequest.of(0, 10));

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
    }
}
