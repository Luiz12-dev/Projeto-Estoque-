package com.metalurgica.estoque.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Uma peça dentro de um orçamento de corte.
 * <p>
 * Guarda uma <b>cópia congelada</b> dos parâmetros da chapa usados no cálculo —
 * dimensões, valor e preço por metro. Um orçamento é um compromisso já
 * comunicado ao cliente: se a chapa for reajustada no mês seguinte, o valor
 * informado não pode mudar sozinho. Sem essa cópia, consultar um orçamento
 * antigo devolveria um número que nunca existiu.
 */
@Entity
@Table(name = "orcamento_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class OrcamentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private Orcamento orcamento;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "largura_peca_mm", nullable = false)
    private BigDecimal larguraPecaMm;

    @Column(name = "comprimento_peca_mm", nullable = false)
    private BigDecimal comprimentoPecaMm;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantidade = 1;

    // Corte interno — zero para quem só corta retângulo
    @Column(name = "quantidade_furos", nullable = false)
    @Builder.Default
    private Integer quantidadeFuros = 0;

    @Column(name = "diametro_furo_mm", nullable = false)
    @Builder.Default
    private BigDecimal diametroFuroMm = BigDecimal.ZERO;

    @Column(name = "corte_extra_metros", nullable = false)
    @Builder.Default
    private BigDecimal corteExtraMetros = BigDecimal.ZERO;

    // Cópia congelada dos parâmetros da chapa
    @Column(name = "largura_chapa_mm", nullable = false)
    private BigDecimal larguraChapaMm;

    @Column(name = "comprimento_chapa_mm", nullable = false)
    private BigDecimal comprimentoChapaMm;

    @Column(name = "valor_chapa", nullable = false)
    private BigDecimal valorChapa;

    @Column(name = "preco_metro_corte", nullable = false)
    private BigDecimal precoMetroCorte;

    // Resultado do cálculo
    @Column(name = "comprimento_corte_metros", nullable = false)
    private BigDecimal comprimentoCorteMetros;

    @Column(name = "custo_material_unitario", nullable = false)
    private BigDecimal custoMaterialUnitario;

    @Column(name = "custo_corte_unitario", nullable = false)
    private BigDecimal custoCorteUnitario;

    @Column(name = "preco_unitario", nullable = false)
    private BigDecimal precoUnitario;

    @Column(name = "preco_total", nullable = false)
    private BigDecimal precoTotal;
}
