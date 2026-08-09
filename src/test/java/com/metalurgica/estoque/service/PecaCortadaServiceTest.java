package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.domain.repository.PecaCortadaRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.request.PecaCortadaRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.PecaCortadaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PecaCortadaServiceTest {

    @InjectMocks
    private PecaCortadaService pecaCortadaService;

    @Mock
    private PecaCortadaRepository pecaCortadaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    @Mock
    private MovimentacaoService movimentacaoService;

    private Produto chapa;
    private OrdemServico os;

    @BeforeEach
    void setUp() {
        Usuario usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);

        chapa = Produto.builder().id(1L).nome("Chapa de Aço 3mm").unidadeMedida("UN").build();
        os = OrdemServico.builder().id(10L).codigo("OS-0001").build();
    }

    @Test
    @DisplayName("Deve calcular o valor da peça proporcional à área ocupada na chapa")
    void deveCalcularValorProporcional() {
        // Chapa de 1000x2000mm (2.000.000 mm²) valendo R$500,00.
        // Peça de 200x100mm (20.000 mm²) = 1% da área da chapa = R$5,00.
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("200"), new BigDecimal("100"), 1, 1L, 10L);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoService.registrar(any(MovimentacaoRequest.class))).thenAnswer(i -> {
            MovimentacaoRequest r = i.getArgument(0);
            return new MovimentacaoResponse(100L, TipoMovimentacao.SAIDA, r.quantidade(), r.valorUnitario(),
                    r.quantidade().multiply(r.valorUnitario()), null, r.observacao(), chapa.getNome(), "Teste",
                    os.getId(), os.getCodigo());
        });
        when(movimentacaoRepository.getReferenceById(100L)).thenReturn(new Movimentacao());
        when(pecaCortadaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PecaCortadaResponse response = pecaCortadaService.criar(request);

        assertThat(response.valorUnitarioCalculado()).isEqualByComparingTo("5.00");
        assertThat(response.valorTotalCalculado()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("Deve multiplicar o valor unitário pela quantidade de peças cortadas")
    void deveMultiplicarPorQuantidade() {
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"),
                new BigDecimal("100"), new BigDecimal("100"), 4, 1L, 10L);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoService.registrar(any(MovimentacaoRequest.class))).thenAnswer(i -> {
            MovimentacaoRequest r = i.getArgument(0);
            return new MovimentacaoResponse(101L, TipoMovimentacao.SAIDA, r.quantidade(), r.valorUnitario(),
                    r.quantidade().multiply(r.valorUnitario()), null, r.observacao(), chapa.getNome(), "Teste",
                    os.getId(), os.getCodigo());
        });
        when(movimentacaoRepository.getReferenceById(101L)).thenReturn(new Movimentacao());
        when(pecaCortadaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PecaCortadaResponse response = pecaCortadaService.criar(request);

        // Área peça / área chapa = 10.000/1.000.000 = 1% de R$100 = R$1,00 por peça.
        assertThat(response.valorUnitarioCalculado()).isEqualByComparingTo("1.00");
        assertThat(response.valorTotalCalculado()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("Deve rejeitar peça maior que a chapa")
    void deveRejeitarPecaMaiorQueChapa() {
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Peça Grande", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50.00"),
                new BigDecimal("200"), new BigDecimal("200"), 1, 1L, 10L);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));

        assertThrows(IllegalArgumentException.class, () -> pecaCortadaService.criar(request));

        verify(movimentacaoService, never()).registrar(any());
        verify(pecaCortadaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve repassar quantidade e valor calculado para a Movimentacao de baixa de estoque")
    void deveRepassarDadosParaMovimentacao() {
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"),
                new BigDecimal("500"), new BigDecimal("500"), 2, 1L, 10L);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoService.registrar(any(MovimentacaoRequest.class))).thenAnswer(i -> {
            MovimentacaoRequest r = i.getArgument(0);
            return new MovimentacaoResponse(102L, TipoMovimentacao.SAIDA, r.quantidade(), r.valorUnitario(),
                    r.quantidade().multiply(r.valorUnitario()), null, r.observacao(), chapa.getNome(), "Teste",
                    os.getId(), os.getCodigo());
        });
        when(movimentacaoRepository.getReferenceById(102L)).thenReturn(new Movimentacao());
        when(pecaCortadaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        pecaCortadaService.criar(request);

        ArgumentCaptor<MovimentacaoRequest> captor = ArgumentCaptor.forClass(MovimentacaoRequest.class);
        verify(movimentacaoService).registrar(captor.capture());
        MovimentacaoRequest sent = captor.getValue();

        assertThat(sent.produtoId()).isEqualTo(1L);
        assertThat(sent.tipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(sent.quantidade()).isEqualByComparingTo("2");
        // Área peça/chapa = 250.000/1.000.000 = 25% de R$100 = R$25,00 por peça.
        assertThat(sent.valorUnitario()).isEqualByComparingTo("25.00");
        assertThat(sent.ordemServicoId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao listar peças de OS inexistente")
    void deveLancarExcecaoParaOsInexistente() {
        when(ordemServicoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> pecaCortadaService.listarPorOrdemServico(99L));
    }
}
