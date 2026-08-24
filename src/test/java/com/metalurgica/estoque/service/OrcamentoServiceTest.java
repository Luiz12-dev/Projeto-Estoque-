package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Empresa;
import com.metalurgica.estoque.domain.entity.Orcamento;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import com.metalurgica.estoque.domain.repository.EmpresaRepository;
import com.metalurgica.estoque.domain.repository.OrcamentoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.OrcamentoItemRequest;
import com.metalurgica.estoque.dto.request.OrcamentoRequest;
import com.metalurgica.estoque.dto.request.OrcamentoUpdateRequest;
import com.metalurgica.estoque.dto.request.SimulacaoOrcamentoRequest;
import com.metalurgica.estoque.dto.response.OrcamentoResponse;
import com.metalurgica.estoque.dto.response.SimulacaoOrcamentoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class OrcamentoServiceTest {

    @InjectMocks
    private OrcamentoService orcamentoService;

    @Mock
    private OrcamentoRepository orcamentoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    /** Calculadora real: o objetivo aqui é validar valores, não interações. */
    @Spy
    private CalculadoraCorte calculadoraCorte = new CalculadoraCorte();

    private Empresa empresa;
    private Produto chapa;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder().id(1L).nome("Cadu").login("cadu").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, null));
        SecurityContextHolder.setContext(context);

        empresa = Empresa.builder().id(1L).nome("Serralheria Silva").build();

        // Chapa 1000x2000mm a R$500,00, corte a R$10,00/m — mesma da tabela de casos
        chapa = Produto.builder()
                .id(7L)
                .nome("Chapa Aço 3mm")
                .unidadeMedida("UN")
                .valorUnitario(new BigDecimal("500.00"))
                .larguraMm(new BigDecimal("1000"))
                .comprimentoMm(new BigDecimal("2000"))
                .precoMetroCorte(new BigDecimal("10.00"))
                .build();
    }

    private OrcamentoItemRequest peca(String nome, String largura, String comprimento, int qtd) {
        return new OrcamentoItemRequest(nome, 7L,
                new BigDecimal(largura), new BigDecimal(comprimento), qtd, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void mockarCriacao() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(produtoRepository.findById(7L)).thenReturn(Optional.of(chapa));
        when(orcamentoRepository.getNextCodigoSequence()).thenReturn(3L);
        when(orcamentoRepository.save(any(Orcamento.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("criação")
    class Criacao {

        @Test
        @DisplayName("gera código sequencial no padrão ORC-0000")
        void deveGerarCodigoSequencial() {
            mockarCriacao();

            OrcamentoResponse r = orcamentoService.criar(new OrcamentoRequest(
                    1L, BigDecimal.ZERO, null, List.of(peca("Suporte", "200", "100", 1))));

            assertThat(r.codigo()).isEqualTo("ORC-0003");
            assertThat(r.situacao()).isEqualTo(SituacaoOrcamento.PENDENTE);
            assertThat(r.empresaNome()).isEqualTo("Serralheria Silva");
        }

        @Test
        @DisplayName("soma o total de vários itens")
        void deveSomarTotalDeVariosItens() {
            mockarCriacao();

            // Peça A: material 5,00 + corte 6,00 = 11,00, x2 = 22,00
            // Peça B: material 5,00 + corte 6,00 = 11,00, x3 = 33,00
            OrcamentoResponse r = orcamentoService.criar(new OrcamentoRequest(
                    1L, BigDecimal.ZERO, null,
                    List.of(peca("Suporte", "200", "100", 2), peca("Flange", "200", "100", 3))));

            assertThat(r.itens()).hasSize(2);
            assertThat(r.itens().get(0).precoTotal()).isEqualByComparingTo("22.00");
            assertThat(r.itens().get(1).precoTotal()).isEqualByComparingTo("33.00");
            assertThat(r.valorTotal()).isEqualByComparingTo("55.00");
        }

        @Test
        @DisplayName("aplica a margem informada no orçamento")
        void deveAplicarMargem() {
            mockarCriacao();

            OrcamentoResponse r = orcamentoService.criar(new OrcamentoRequest(
                    1L, new BigDecimal("30"), null, List.of(peca("Suporte", "200", "100", 1))));

            // Subtotal 11,00 com 30% = 14,30
            assertThat(r.itens().get(0).precoUnitario()).isEqualByComparingTo("14.30");
            assertThat(r.valorTotal()).isEqualByComparingTo("14.30");
        }

        @Test
        @DisplayName("congela os parâmetros da chapa no item, para o orçamento não mudar depois")
        void deveCongelarParametrosDaChapa() {
            mockarCriacao();

            OrcamentoResponse r = orcamentoService.criar(new OrcamentoRequest(
                    1L, BigDecimal.ZERO, null, List.of(peca("Suporte", "200", "100", 1))));

            var item = r.itens().get(0);
            assertThat(item.larguraChapaMm()).isEqualByComparingTo("1000");
            assertThat(item.comprimentoChapaMm()).isEqualByComparingTo("2000");
            assertThat(item.valorChapa()).isEqualByComparingTo("500.00");
            assertThat(item.precoMetroCorte()).isEqualByComparingTo("10.00");

            // Reajuste posterior da chapa não pode alcançar o item já gravado
            chapa.setValorUnitario(new BigDecimal("900.00"));
            assertThat(item.valorChapa()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("o detalhamento de cada item soma exatamente o subtotal")
        void detalhamentoDeveFechar() {
            mockarCriacao();

            OrcamentoResponse r = orcamentoService.criar(new OrcamentoRequest(
                    1L, new BigDecimal("30"), null, List.of(peca("Suporte", "237", "113", 1))));

            var item = r.itens().get(0);
            assertThat(item.custoMaterialUnitario().add(item.custoCorteUnitario()))
                    .isEqualByComparingTo(item.subtotalUnitario());
        }

        @Test
        @DisplayName("recusa empresa inexistente")
        void deveRecusarEmpresaInexistente() {
            when(empresaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orcamentoService.criar(new OrcamentoRequest(
                    99L, BigDecimal.ZERO, null, List.of(peca("Suporte", "200", "100", 1)))))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(orcamentoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("chapa sem parâmetros de corte")
    class ChapaIncompleta {

        @Test
        @DisplayName("recusa e diz exatamente qual campo falta e onde resolver")
        void deveRecusarChapaSemPrecoDeCorte() {
            chapa.setPrecoMetroCorte(null);
            when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
            when(produtoRepository.findById(7L)).thenReturn(Optional.of(chapa));

            assertThatThrownBy(() -> orcamentoService.criar(new OrcamentoRequest(
                    1L, BigDecimal.ZERO, null, List.of(peca("Suporte", "200", "100", 1)))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chapa Aço 3mm")
                    .hasMessageContaining("preço por metro de corte")
                    .hasMessageContaining("tela de Produtos");
        }

        @Test
        @DisplayName("lista todos os campos faltantes de uma vez")
        void deveListarTodosOsCamposFaltantes() {
            chapa.setLarguraMm(null);
            chapa.setPrecoMetroCorte(null);
            when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
            when(produtoRepository.findById(7L)).thenReturn(Optional.of(chapa));

            assertThatThrownBy(() -> orcamentoService.criar(new OrcamentoRequest(
                    1L, BigDecimal.ZERO, null, List.of(peca("Suporte", "200", "100", 1)))))
                    .hasMessageContaining("largura da chapa")
                    .hasMessageContaining("preço por metro de corte");
        }
    }

    @Nested
    @DisplayName("simulação")
    class Simulacao {

        @Test
        @DisplayName("calcula sem gravar nada")
        void deveCalcularSemPersistir() {
            when(produtoRepository.findById(7L)).thenReturn(Optional.of(chapa));

            SimulacaoOrcamentoResponse r = orcamentoService.simular(new SimulacaoOrcamentoRequest(
                    new BigDecimal("30"),
                    List.of(peca("Suporte", "200", "100", 10))));

            assertThat(r.itens()).hasSize(1);
            assertThat(r.itens().get(0).id()).as("simulação não persiste, então não tem id").isNull();
            assertThat(r.itens().get(0).precoUnitario()).isEqualByComparingTo("14.30");
            assertThat(r.valorTotal()).isEqualByComparingTo("143.00");

            verify(orcamentoRepository, never()).save(any());
            verify(empresaRepository, never()).findById(any());
        }

        @Test
        @DisplayName("produz o mesmo valor que a criação, para o preview não enganar")
        void simulacaoDeveBaterComCriacao() {
            when(produtoRepository.findById(7L)).thenReturn(Optional.of(chapa));

            var itens = List.of(peca("Suporte", "200", "100", 7));
            SimulacaoOrcamentoResponse simulado = orcamentoService.simular(
                    new SimulacaoOrcamentoRequest(new BigDecimal("30"), itens));

            when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
            when(orcamentoRepository.getNextCodigoSequence()).thenReturn(1L);
            when(orcamentoRepository.save(any(Orcamento.class))).thenAnswer(i -> i.getArgument(0));

            OrcamentoResponse gravado = orcamentoService.criar(
                    new OrcamentoRequest(1L, new BigDecimal("30"), null, itens));

            assertThat(simulado.valorTotal()).isEqualByComparingTo(gravado.valorTotal());
        }
    }

    @Nested
    @DisplayName("atualização")
    class Atualizacao {

        @Test
        @DisplayName("muda a situação sem tocar nos valores")
        void deveMudarSituacao() {
            Orcamento existente = Orcamento.builder()
                    .id(5L).codigo("ORC-0005").empresa(empresa)
                    .situacao(SituacaoOrcamento.PENDENTE)
                    .margemPercentual(new BigDecimal("30"))
                    .valorTotal(new BigDecimal("143.00"))
                    .usuario(Usuario.builder().id(1L).nome("Cadu").build())
                    .build();

            when(orcamentoRepository.buscarComItens(5L)).thenReturn(Optional.of(existente));
            when(orcamentoRepository.save(any(Orcamento.class))).thenAnswer(i -> i.getArgument(0));

            OrcamentoResponse r = orcamentoService.atualizar(5L,
                    new OrcamentoUpdateRequest(SituacaoOrcamento.APROVADO, null));

            assertThat(r.situacao()).isEqualTo(SituacaoOrcamento.APROVADO);
            assertThat(r.valorTotal()).isEqualByComparingTo("143.00");
        }

        @Test
        @DisplayName("recusa orçamento inexistente")
        void deveRecusarOrcamentoInexistente() {
            when(orcamentoRepository.buscarComItens(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orcamentoService.atualizar(99L,
                    new OrcamentoUpdateRequest(SituacaoOrcamento.APROVADO, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }
}
