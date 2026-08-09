package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Empresa;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.domain.repository.EmpresaRepository;
import com.metalurgica.estoque.domain.repository.MovimentacaoRepository;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import com.metalurgica.estoque.dto.request.OrdemServicoRequest;
import com.metalurgica.estoque.dto.request.OrdemServicoUpdateRequest;
import com.metalurgica.estoque.dto.response.ContagemOsProjection;
import com.metalurgica.estoque.dto.response.CustoOsProjection;
import com.metalurgica.estoque.dto.response.OrdemServicoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceTest {

    @InjectMocks
    private OrdemServicoService ordemServicoService;

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private PdfService pdfService;

    private Usuario usuarioLogado;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        usuarioLogado = Usuario.builder().id(1L).nome("Teste").login("teste").build();
        empresa = Empresa.builder().id(1L).nome("Cliente ABC").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuarioLogado, null, null));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Deve criar OS com código gerado pela sequence")
    void deveCriarOsComCodigo() {
        when(ordemServicoRepository.getNextCodigoSequence()).thenReturn(42L);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            os.setId(1L);
            return os;
        });

        OrdemServicoRequest request = new OrdemServicoRequest(
                "Manutenção de torno", 1L, PrioridadeOrdemServico.ALTA, null, BigDecimal.ZERO);

        OrdemServicoResponse response = ordemServicoService.criar(request);

        assertThat(response.codigo()).isEqualTo("OS-0042");
        assertThat(response.status()).isEqualTo(StatusOrdemServico.ABERTA);
        assertThat(response.prioridade()).isEqualTo(PrioridadeOrdemServico.ALTA);
        assertThat(response.descricao()).isEqualTo("Manutenção de torno");
        assertThat(response.empresaNome()).isEqualTo("Cliente ABC");
        verify(ordemServicoRepository).getNextCodigoSequence();
        verify(ordemServicoRepository).save(any(OrdemServico.class));
    }

    @Test
    @DisplayName("Deve usar prioridade MEDIA como padrão ao criar OS sem prioridade")
    void deveCriarOsComPrioridadePadrao() {
        when(ordemServicoRepository.getNextCodigoSequence()).thenReturn(1L);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            os.setId(1L);
            return os;
        });

        OrdemServicoRequest request = new OrdemServicoRequest(
                "Reparo", 1L, null, null, BigDecimal.ZERO);

        OrdemServicoResponse response = ordemServicoService.criar(request);

        assertThat(response.prioridade()).isEqualTo(PrioridadeOrdemServico.MEDIA);
    }

    @Test
    @DisplayName("Deve atualizar status da OS para CONCLUIDA e definir data de conclusão")
    void deveAtualizarStatusParaConcluida() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.EM_ANDAMENTO)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any())).thenReturn(List.of());
        when(movimentacaoRepository.contarPorOsIds(any())).thenReturn(List.of());

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                null, null, StatusOrdemServico.CONCLUIDA, null, null, null);

        OrdemServicoResponse response = ordemServicoService.atualizar(1L, request);

        assertThat(response.status()).isEqualTo(StatusOrdemServico.CONCLUIDA);
        assertThat(os.getDataConclusao()).isNotNull();
    }

    @Test
    @DisplayName("Deve limpar data de conclusão ao reabrir OS")
    void deveLimparDataConclusaoAoReabrir() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.CONCLUIDA)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .dataConclusao(LocalDateTime.now())
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any())).thenReturn(List.of());
        when(movimentacaoRepository.contarPorOsIds(any())).thenReturn(List.of());

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                null, null, StatusOrdemServico.EM_ANDAMENTO, null, null, null);

        ordemServicoService.atualizar(1L, request);

        assertThat(os.getDataConclusao()).isNull();
    }

    @Test
    @DisplayName("Deve atualizar campos individuais sem alterar os demais")
    void deveAtualizarCamposIndividuais() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Desc original")
                .empresa(empresa)
                .status(StatusOrdemServico.ABERTA)
                .prioridade(PrioridadeOrdemServico.BAIXA)
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any())).thenReturn(List.of());
        when(movimentacaoRepository.contarPorOsIds(any())).thenReturn(List.of());

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                "Nova descrição", null, null, PrioridadeOrdemServico.URGENTE, null, null);

        ordemServicoService.atualizar(1L, request);

        assertThat(os.getDescricao()).isEqualTo("Nova descrição");
        assertThat(os.getEmpresa()).isEqualTo(empresa);
        assertThat(os.getPrioridade()).isEqualTo(PrioridadeOrdemServico.URGENTE);
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar OS inexistente")
    void deveLancarExcecaoParaOsInexistente() {
        when(ordemServicoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> {
            ordemServicoService.buscarPorId(99L);
        });
    }

    @Test
    @DisplayName("Deve buscar OS por ID com custos e contagem de movimentações")
    void deveBuscarPorIdComCustosEContagem() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.ABERTA)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(movimentacaoRepository.somarCustosPorOsIds(List.of(1L)))
                .thenReturn(List.of(new CustoOsProjection(1L, new BigDecimal("500.00"))));
        when(movimentacaoRepository.contarPorOsIds(List.of(1L)))
                .thenReturn(List.of(new ContagemOsProjection(1L, 3L)));

        OrdemServicoResponse response = ordemServicoService.buscarPorId(1L);

        assertThat(response.custoTotal()).isEqualByComparingTo("500.00");
        assertThat(response.totalMovimentacoes()).isEqualTo(3);
    }

    @Test
    @DisplayName("Deve persistir o valor de mão de obra informado ao criar a OS")
    void deveCriarOsComValorMaoDeObra() {
        when(ordemServicoRepository.getNextCodigoSequence()).thenReturn(7L);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            os.setId(1L);
            return os;
        });

        OrdemServicoRequest request = new OrdemServicoRequest(
                "Solda de estrutura", 1L, PrioridadeOrdemServico.MEDIA, null, new BigDecimal("750.00"));

        OrdemServicoResponse response = ordemServicoService.criar(request);

        assertThat(response.valorMaoDeObra()).isEqualByComparingTo("750.00");
        // Sem material lançado ainda, o custo total já deve refletir a mão de obra
        assertThat(response.custoTotal()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Deve usar zero, e não null, quando a OS é criada sem mão de obra")
    void deveCriarOsSemMaoDeObraComoZero() {
        when(ordemServicoRepository.getNextCodigoSequence()).thenReturn(8L);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            os.setId(1L);
            return os;
        });

        OrdemServicoRequest request = new OrdemServicoRequest(
                "Corte simples", 1L, null, null, null);

        OrdemServicoResponse response = ordemServicoService.criar(request);

        assertThat(response.valorMaoDeObra()).isNotNull().isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Deve somar mão de obra ao custo de material no custo total da OS")
    void deveSomarMaoDeObraAoCustoDeMaterial() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Fabricação de portão")
                .empresa(empresa)
                .status(StatusOrdemServico.EM_ANDAMENTO)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .valorMaoDeObra(new BigDecimal("250.00"))
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(movimentacaoRepository.somarCustosPorOsIds(List.of(1L)))
                .thenReturn(List.of(new CustoOsProjection(1L, new BigDecimal("500.00"))));
        when(movimentacaoRepository.contarPorOsIds(List.of(1L)))
                .thenReturn(List.of(new ContagemOsProjection(1L, 2L)));

        OrdemServicoResponse response = ordemServicoService.buscarPorId(1L);

        // A ponta que o bug quebrava: R$ 500,00 de material + R$ 250,00 de mão de obra
        assertThat(response.custoTotal()).isEqualByComparingTo("750.00");
        assertThat(response.valorMaoDeObra()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("Deve atualizar o valor de mão de obra e refletir no custo total")
    void deveAtualizarValorMaoDeObra() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.ABERTA)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .valorMaoDeObra(BigDecimal.ZERO)
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any()))
                .thenReturn(List.of(new CustoOsProjection(1L, new BigDecimal("100.00"))));
        when(movimentacaoRepository.contarPorOsIds(any()))
                .thenReturn(List.of(new ContagemOsProjection(1L, 1L)));

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                null, null, null, null, null, new BigDecimal("300.00"));

        OrdemServicoResponse response = ordemServicoService.atualizar(1L, request);

        assertThat(os.getValorMaoDeObra()).isEqualByComparingTo("300.00");
        assertThat(response.custoTotal()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Deve permitir zerar a mão de obra de uma OS")
    void devePermitirZerarMaoDeObra() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.ABERTA)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .valorMaoDeObra(new BigDecimal("400.00"))
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any())).thenReturn(List.of());
        when(movimentacaoRepository.contarPorOsIds(any())).thenReturn(List.of());

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                null, null, null, null, null, BigDecimal.ZERO);

        ordemServicoService.atualizar(1L, request);

        assertThat(os.getValorMaoDeObra()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Não deve alterar a mão de obra quando o campo não é informado na atualização")
    void naoDeveAlterarMaoDeObraQuandoAusente() {
        OrdemServico os = OrdemServico.builder()
                .id(1L)
                .codigo("OS-0001")
                .descricao("Teste")
                .empresa(empresa)
                .status(StatusOrdemServico.ABERTA)
                .prioridade(PrioridadeOrdemServico.MEDIA)
                .valorMaoDeObra(new BigDecimal("180.00"))
                .usuario(usuarioLogado)
                .build();

        when(ordemServicoRepository.findById(1L)).thenReturn(Optional.of(os));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(movimentacaoRepository.somarCustosPorOsIds(any())).thenReturn(List.of());
        when(movimentacaoRepository.contarPorOsIds(any())).thenReturn(List.of());

        OrdemServicoUpdateRequest request = new OrdemServicoUpdateRequest(
                "Nova descrição", null, null, null, null, null);

        ordemServicoService.atualizar(1L, request);

        assertThat(os.getValorMaoDeObra()).isEqualByComparingTo("180.00");
    }
}
