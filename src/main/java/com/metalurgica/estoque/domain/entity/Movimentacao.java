package com.metalurgica.estoque.domain.entity;

import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimentacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 19, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
