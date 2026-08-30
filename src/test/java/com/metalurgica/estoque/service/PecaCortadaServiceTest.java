package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Empresa;
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
import org.mockito.Spy;
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

    /**
     * Calculadora real, não mock: os valores esperados abaixo são a regressão
     * que garante que extrair a fórmula para CalculadoraCorte não mudou nada do
     * que o registro de corte já produzia. Com um mock, a conta não rodaria e o
     * teste não provaria coisa alguma.
     */
    @Spy
    private CalculadoraCorte calculadoraCorte = new CalculadoraCorte();

    private Produto chapa;
    private OrdemServico os;

    @BeforeEach
    void setUp() {
        Usuario usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);

        chapa = Produto.builder().id(1L).nome("Chapa de Aço 3mm").unidadeMedida("UN").build();
        // Toda OS pertence a uma empresa (empresa_id NOT NULL): sem ela, o fixture
        // representaria um estado que o banco não aceita.
        os = OrdemServico.builder()
                .id(10L)
                .codigo("OS-0001")
                .empresa(Empresa.builder().id(1L).nome("Serralheria Silva").build())
                .build();
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
    @DisplayName("Debita a FRACAO da chapa consumida, nao o numero de pecas")
    void debitaFracaoDaChapaNaoNumeroDePecas() {
        // O bug que este teste tranca: chapa de 1000x2000 mm (2.000.000 mm²),
        // duas pecas de 200x100 mm (20.000 mm² cada) ocupam 1% cada, 2% no
        // total. O estoque dava baixa de DUAS CHAPAS INTEIRAS enquanto o custo
        // dizia que as duas juntas valiam 2% da chapa.
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("200"), new BigDecimal("100"), 2, 1L, 10L);

        prepararRegistro(200L);

        pecaCortadaService.criar(request);

        MovimentacaoRequest enviado = capturarMovimentacao();
        assertThat(enviado.quantidade())
                .as("duas pecas de 1% consomem 0,02 chapa, nao 2 chapas")
                .isEqualByComparingTo("0.0200");
    }

    @Test
    @DisplayName("O valor total da movimentacao continua sendo o custo real do material")
    void valorTotalDaMovimentacaoBateComOCustoDoMaterial() {
        // A unidade virou a chapa, entao o valor unitario tem de ser o preco da
        // chapa. Se ficasse o custo por peca, o total sairia errado por ordens
        // de grandeza e o estoque seria valorizado a menos.
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("200"), new BigDecimal("100"), 2, 1L, 10L);

        prepararRegistro(201L);

        PecaCortadaResponse response = pecaCortadaService.criar(request);
        MovimentacaoRequest enviado = capturarMovimentacao();

        assertThat(enviado.valorUnitario())
                .as("a unidade e a chapa, entao o valor unitario e o preco dela")
                .isEqualByComparingTo("500.00");

        BigDecimal totalDaMovimentacao = enviado.quantidade().multiply(enviado.valorUnitario());
        assertThat(totalDaMovimentacao)
                .as("0,02 chapa x R$ 500 tem de dar o mesmo que 2 pecas a R$ 5,00")
                .isEqualByComparingTo(response.valorTotalCalculado());
    }

    @Test
    @DisplayName("Peca que ocupa a chapa inteira consome exatamente uma chapa")
    void pecaDoTamanhoDaChapaConsomeUmaChapa() {
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Chapa inteira", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("1000"), new BigDecimal("2000"), 1, 1L, 10L);

        prepararRegistro(202L);

        pecaCortadaService.criar(request);

        assertThat(capturarMovimentacao().quantidade()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("Peca minuscula consome o minimo representavel, nunca zero")
    void pecaMinusculaNaoSaiDeGraca() {
        // 5x5 mm numa chapa de 1000x2000 mm da 0,0000125 — abaixo das quatro
        // casas do estoque. Debitar zero faria material sair de graca.
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Arruela", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("5"), new BigDecimal("5"), 1, 1L, 10L);

        prepararRegistro(203L);

        pecaCortadaService.criar(request);

        assertThat(capturarMovimentacao().quantidade())
                .as("nunca zero")
                .isEqualByComparingTo("0.0001");
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
        assertThat(sent.ordemServicoId()).isEqualTo(10L);

        // Este teste afirmava quantidade = 2 e valor unitário = R$ 25,00, ou
        // seja, travava justamente o defeito: duas peças davam baixa de duas
        // chapas inteiras. Área da peça/chapa = 250.000/1.000.000 = 25%; duas
        // peças consomem meia chapa, e a unidade da movimentação é a chapa.
        assertThat(sent.quantidade()).isEqualByComparingTo("0.5000");
        assertThat(sent.valorUnitario()).isEqualByComparingTo("100.00");
        assertThat(sent.quantidade().multiply(sent.valorUnitario()))
                .as("meia chapa a R$ 100 = R$ 50, o mesmo que 2 peças a R$ 25")
                .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Deve lançar exceção ao listar peças de OS inexistente")
    void deveLancarExcecaoParaOsInexistente() {
        when(ordemServicoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> pecaCortadaService.listarPorOrdemServico(99L));
    }

    @Test
    @DisplayName("O registro de corte não aplica custo de corte nem margem — só material")
    void registroNaoAplicaCorteNemMargem() {
        // Mesma peça do caso base da tabela compartilhada. No orçamento, com preço
        // por metro e margem, ela custaria mais. Aqui tem de sair exatamente o
        // rateio do material, porque este valor alimenta o estoque.
        PecaCortadaRequest request = new PecaCortadaRequest(
                "Suporte L", new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                new BigDecimal("200"), new BigDecimal("100"), 1, 1L, 10L);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoService.registrar(any(MovimentacaoRequest.class))).thenAnswer(i -> {
            MovimentacaoRequest r = i.getArgument(0);
            return new MovimentacaoResponse(103L, TipoMovimentacao.SAIDA, r.quantidade(), r.valorUnitario(),
                    r.quantidade().multiply(r.valorUnitario()), null, r.observacao(), chapa.getNome(), "Teste",
                    os.getId(), os.getCodigo());
        });
        when(movimentacaoRepository.getReferenceById(103L)).thenReturn(new Movimentacao());
        when(pecaCortadaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PecaCortadaResponse response = pecaCortadaService.criar(request);

        assertThat(response.valorUnitarioCalculado()).isEqualByComparingTo("5.00");
        verify(calculadoraCorte).calcularCustoMaterial(any(), any(), any(), any(), any());
        verify(calculadoraCorte, never()).calcular(any());
    }

    // ---------------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------------

    /** Prepara os mocks do caminho feliz de registro de corte. */
    private void prepararRegistro(Long idMovimentacao) {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(chapa));
        when(ordemServicoRepository.findById(10L)).thenReturn(Optional.of(os));
        when(movimentacaoService.registrar(any(MovimentacaoRequest.class))).thenAnswer(i -> {
            MovimentacaoRequest r = i.getArgument(0);
            return new MovimentacaoResponse(idMovimentacao, TipoMovimentacao.SAIDA, r.quantidade(),
                    r.valorUnitario(), r.quantidade().multiply(r.valorUnitario()), null, r.observacao(),
                    chapa.getNome(), "Teste", os.getId(), os.getCodigo());
        });
        when(movimentacaoRepository.getReferenceById(idMovimentacao)).thenReturn(new Movimentacao());
        when(pecaCortadaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MovimentacaoRequest capturarMovimentacao() {
        ArgumentCaptor<MovimentacaoRequest> captor = ArgumentCaptor.forClass(MovimentacaoRequest.class);
        verify(movimentacaoService).registrar(captor.capture());
        return captor.getValue();
    }
}
