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
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(length = 100)
    private String categoria;

    @Column(name = "quantidade_atual", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantidadeAtual;

    @Column(name = "quantidade_minima", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantidadeMinima;

    @Column(name = "unidade_medida", nullable = false, length = 10)
    private String unidadeMedida;

    @Column(name = "valor_unitario", precision = 19, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean isEstoqueBaixo() {
        return this.quantidadeAtual.compareTo(this.quantidadeMinima) < 0;
    }
}
