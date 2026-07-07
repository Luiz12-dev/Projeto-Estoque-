package com.metalurgica.estoque.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String nome;

    private String categoria;

    @Column(name = "quantidade_atual", nullable = false)
    private BigDecimal quantidadeAtual;

    @Column(name = "quantidade_minima", nullable = false)
    private BigDecimal quantidadeMinima;

    @Column(name = "unidade_medida", nullable = false)
    private String unidadeMedida;

    @Column(name = "valor_unitario")
    private BigDecimal valorUnitario;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public boolean isEstoqueBaixo() {
        return quantidadeAtual.compareTo(quantidadeMinima) < 0;
    }

    public void adicionarEstoque(BigDecimal quantidade) {
        this.quantidadeAtual = this.quantidadeAtual.add(quantidade);
    }

    public void baixarEstoque(BigDecimal quantidade) {
        BigDecimal novoEstoque = this.quantidadeAtual.subtract(quantidade);
        if (novoEstoque.compareTo(BigDecimal.ZERO) < 0) {
            throw new com.metalurgica.estoque.exception.EstoqueInsuficienteException(
                    String.format("Estoque insuficiente para o produto '%s'. Disponível: %s %s",
                            this.nome,
                            this.quantidadeAtual.stripTrailingZeros().toPlainString(),
                            this.unidadeMedida));
        }
        this.quantidadeAtual = novoEstoque;
    }
}
