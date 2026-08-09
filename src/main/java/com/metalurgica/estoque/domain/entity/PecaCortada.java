package com.metalurgica.estoque.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de uma peça cortada na máquina de corte a laser, vinculada a uma
 * OS/Empresa. Guarda as dimensões usadas no cálculo (chapa de origem e peça)
 * e o valor unitário resultante — rateio proporcional pela área da chapa.
 * O consumo da chapa em estoque é registrado através de uma Movimentacao
 * (SAIDA) associada, reaproveitando as mesmas regras de baixa de estoque.
 */
@Entity
@Table(name = "peca_cortada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PecaCortada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "largura_chapa_mm", nullable = false)
    private BigDecimal larguraChapaMm;

    @Column(name = "comprimento_chapa_mm", nullable = false)
    private BigDecimal comprimentoChapaMm;

    @Column(name = "valor_chapa", nullable = false)
    private BigDecimal valorChapa;

    @Column(name = "largura_peca_mm", nullable = false)
    private BigDecimal larguraPecaMm;

    @Column(name = "comprimento_peca_mm", nullable = false)
    private BigDecimal comprimentoPecaMm;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantidade = 1;

    @Column(name = "valor_unitario_calculado", nullable = false)
    private BigDecimal valorUnitarioCalculado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimentacao_id")
    private Movimentacao movimentacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
