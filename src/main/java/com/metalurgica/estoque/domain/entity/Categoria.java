package com.metalurgica.estoque.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Prateleira do estoque: chapas, discos de corte, tubos.
 * <p>
 * Existe por conta própria, e não como texto dentro do produto, porque o dono
 * precisa criar a prateleira antes de ter o que colocar nela — e porque
 * categoria digitada à mão se duplica sozinha: no banco de ensaio já havia
 * "disco" e "Discos de corte" como coisas distintas.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Único ignorando caixa — a unicidade é garantida por índice no banco. */
    @Column(nullable = false, length = 80)
    private String nome;
}
