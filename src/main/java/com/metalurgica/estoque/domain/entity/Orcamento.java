package com.metalurgica.estoque.domain.entity;

import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orçamento de corte a laser — o que se cobra do cliente.
 * <p>
 * Distinto de {@link PecaCortada}, que registra um corte já realizado e lança
 * apenas o material no estoque. O orçamento responde "quanto cobrar"; a peça
 * cortada responde "quanto de material saiu".
 * <p>
 * Tem vários itens porque o cliente raramente pede uma peça só: normalmente
 * manda uma lista, e ela precisa ser um orçamento único, com um total.
 */
@Entity
@Table(name = "orcamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Orcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SituacaoOrcamento situacao = SituacaoOrcamento.PENDENTE;

    @Column(name = "margem_percentual", nullable = false)
    @Builder.Default
    private BigDecimal margemPercentual = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrcamentoItem> itens = new ArrayList<>();

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = LocalDateTime.now();
        if (situacao == null) {
            situacao = SituacaoOrcamento.PENDENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    /** Mantém os dois lados da relação em dia e recalcula o total. */
    public void adicionarItem(OrcamentoItem item) {
        item.setOrcamento(this);
        this.itens.add(item);
        recalcularTotal();
    }

    public void recalcularTotal() {
        this.valorTotal = this.itens.stream()
                .map(OrcamentoItem::getPrecoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
