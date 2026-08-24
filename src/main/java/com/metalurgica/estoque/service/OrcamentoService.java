package com.metalurgica.estoque.service;

import com.metalurgica.estoque.config.SecurityUtils;
import com.metalurgica.estoque.domain.entity.Empresa;
import com.metalurgica.estoque.domain.entity.Orcamento;
import com.metalurgica.estoque.domain.entity.OrcamentoItem;
import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import com.metalurgica.estoque.domain.repository.EmpresaRepository;
import com.metalurgica.estoque.domain.repository.OrcamentoRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.OrcamentoItemRequest;
import com.metalurgica.estoque.dto.request.OrcamentoRequest;
import com.metalurgica.estoque.dto.request.OrcamentoUpdateRequest;
import com.metalurgica.estoque.dto.request.SimulacaoOrcamentoRequest;
import com.metalurgica.estoque.dto.response.OrcamentoItemResponse;
import com.metalurgica.estoque.dto.response.OrcamentoResponse;
import com.metalurgica.estoque.dto.response.SimulacaoOrcamentoResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orçamentos de corte: quanto cobrar do cliente.
 * <p>
 * O cálculo em si mora na {@link CalculadoraCorte}, que é a fonte autoritativa.
 * Este serviço sempre recalcula a partir dos parâmetros — valor que chega do
 * cliente nunca é aproveitado.
 */
@Service
@RequiredArgsConstructor
public class OrcamentoService {

    private final OrcamentoRepository orcamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoRepository produtoRepository;
    private final CalculadoraCorte calculadoraCorte;

    /** Calcula sem gravar nada. */
    @Transactional(readOnly = true)
    public SimulacaoOrcamentoResponse simular(SimulacaoOrcamentoRequest request) {
        List<OrcamentoItemResponse> itens = request.itens().stream()
                .map(item -> OrcamentoItemResponse.fromEntity(
                        montarItem(item, request.margemPercentual())))
                .toList();

        BigDecimal total = itens.stream()
                .map(OrcamentoItemResponse::precoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SimulacaoOrcamentoResponse(request.margemPercentual(), total, itens);
    }

    @Transactional
    public OrcamentoResponse criar(OrcamentoRequest request) {
        Empresa empresa = empresaRepository.findById(request.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Empresa não encontrada com ID: " + request.empresaId()));

        Orcamento orcamento = Orcamento.builder()
                .codigo(gerarProximoCodigo())
                .empresa(empresa)
                .situacao(SituacaoOrcamento.PENDENTE)
                .margemPercentual(request.margemPercentual())
                .observacao(request.observacao())
                .usuario(SecurityUtils.getUsuarioLogado())
                .build();

        request.itens().forEach(itemRequest -> orcamento.adicionarItem(
                montarItem(itemRequest, request.margemPercentual())));

        return OrcamentoResponse.fromEntity(orcamentoRepository.save(orcamento));
    }

    @Transactional(readOnly = true)
    public Page<OrcamentoResponse> listar(String busca, Long empresaId, SituacaoOrcamento situacao,
            LocalDateTime dataInicio, LocalDateTime dataFim, Pageable pageable) {

        String termo = (busca != null && !busca.isBlank()) ? busca.trim() : null;

        return orcamentoRepository.buscar(termo, empresaId, situacao, dataInicio, dataFim, pageable)
                .map(OrcamentoResponse::fromEntitySemItens);
    }

    @Transactional(readOnly = true)
    public OrcamentoResponse buscarPorId(Long id) {
        return OrcamentoResponse.fromEntity(buscarEntidade(id));
    }

    /**
     * Só situação e observação mudam. Os itens e valores ficam congelados: o
     * orçamento já foi comunicado ao cliente, e preço diferente pede orçamento novo.
     */
    @Transactional
    public OrcamentoResponse atualizar(Long id, OrcamentoUpdateRequest request) {
        Orcamento orcamento = buscarEntidade(id);

        if (request.situacao() != null) {
            orcamento.setSituacao(request.situacao());
        }
        if (request.observacao() != null) {
            orcamento.setObservacao(request.observacao());
        }

        return OrcamentoResponse.fromEntity(orcamentoRepository.save(orcamento));
    }

    // ---------- internos ----------

    private Orcamento buscarEntidade(Long id) {
        return orcamentoRepository.buscarComItens(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento não encontrado com ID: " + id));
    }

    /**
     * Monta um item já calculado, congelando os parâmetros da chapa usados.
     * A cópia é o que impede um orçamento antigo de mudar de valor sozinho
     * quando o preço da chapa for reajustado.
     */
    private OrcamentoItem montarItem(OrcamentoItemRequest request, BigDecimal margemPercentual) {
        Produto chapa = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto não encontrado com ID: " + request.produtoId()));

        validarParametrosDeCorte(chapa);

        CalculadoraCorte.Resultado resultado = calculadoraCorte.calcular(new CalculadoraCorte.Entrada(
                chapa.getLarguraMm(),
                chapa.getComprimentoMm(),
                chapa.getValorUnitario(),
                chapa.getPrecoMetroCorte(),
                request.larguraPecaMm(),
                request.comprimentoPecaMm(),
                request.quantidade(),
                request.quantidadeFuros(),
                request.diametroFuroMm(),
                request.corteExtraMetros(),
                margemPercentual));

        return OrcamentoItem.builder()
                .nome(request.nome())
                .produto(chapa)
                .larguraPecaMm(request.larguraPecaMm())
                .comprimentoPecaMm(request.comprimentoPecaMm())
                .quantidade(request.quantidade())
                .quantidadeFuros(zeroSeNulo(request.quantidadeFuros()))
                .diametroFuroMm(zeroSeNulo(request.diametroFuroMm()))
                .corteExtraMetros(zeroSeNulo(request.corteExtraMetros()))
                // Cópia congelada dos parâmetros da chapa
                .larguraChapaMm(chapa.getLarguraMm())
                .comprimentoChapaMm(chapa.getComprimentoMm())
                .valorChapa(chapa.getValorUnitario())
                .precoMetroCorte(chapa.getPrecoMetroCorte())
                .comprimentoCorteMetros(resultado.comprimentoCorteMetros())
                .custoMaterialUnitario(resultado.custoMaterialUnitario())
                .custoCorteUnitario(resultado.custoCorteUnitario())
                .precoUnitario(resultado.precoUnitario())
                .precoTotal(resultado.precoTotal())
                .build();
    }

    /**
     * Mensagem nomeando exatamente o que falta e onde resolver — quem está no
     * balcão com o cliente ao telefone não deve ter que adivinhar.
     */
    private void validarParametrosDeCorte(Produto chapa) {
        if (chapa.isChapaParametrizada()) {
            return;
        }

        List<String> faltando = new java.util.ArrayList<>();
        if (chapa.getLarguraMm() == null || chapa.getLarguraMm().compareTo(BigDecimal.ZERO) <= 0) {
            faltando.add("largura da chapa");
        }
        if (chapa.getComprimentoMm() == null || chapa.getComprimentoMm().compareTo(BigDecimal.ZERO) <= 0) {
            faltando.add("comprimento da chapa");
        }
        if (chapa.getValorUnitario() == null || chapa.getValorUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            faltando.add("valor unitário");
        }
        if (chapa.getPrecoMetroCorte() == null) {
            faltando.add("preço por metro de corte");
        }

        throw new IllegalArgumentException(String.format(
                "A chapa '%s' não pode ser usada em orçamento: falta %s. Preencha os parâmetros de corte na tela de Produtos.",
                chapa.getNome(), String.join(", ", faltando)));
    }

    private String gerarProximoCodigo() {
        return String.format("ORC-%04d", orcamentoRepository.getNextCodigoSequence());
    }

    private static Integer zeroSeNulo(Integer valor) {
        return valor != null ? valor : 0;
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
