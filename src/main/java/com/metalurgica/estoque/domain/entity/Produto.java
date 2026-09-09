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

    /**
     * LAZY seria pior aqui: praticamente toda leitura de produto mostra a
     * categoria junto, e o lazy custaria uma consulta por linha da listagem.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "quantidade_atual", nullable = false)
    private BigDecimal quantidadeAtual;

    @Column(name = "quantidade_minima", nullable = false)
    private BigDecimal quantidadeMinima;

    @Column(name = "unidade_medida", nullable = false)
    private String unidadeMedida;

    @Column(name = "valor_unitario")
    private BigDecimal valorUnitario;

    /**
     * Parâmetros de corte da chapa. Opcionais — só fazem sentido para produtos
     * que são chapa; parafuso e tinta não têm perímetro. Quando os três estão
     * preenchidos (junto de valorUnitario), o produto fica disponível no módulo
     * de Cortes e o usuário só precisa informar as medidas da peça.
     * <p>
     * A espessura não tem campo próprio: cada espessura já é um Produto
     * distinto no estoque, com preço e saldo próprios, então ela fica implícita
     * em qual chapa foi escolhida.
     */
    @Column(name = "largura_mm")
    private BigDecimal larguraMm;

    @Column(name = "comprimento_mm")
    private BigDecimal comprimentoMm;

    @Column(name = "preco_metro_corte")
    private BigDecimal precoMetroCorte;

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

    /**
     * Indica se este produto pode ser usado no módulo de Cortes. Exige os três
     * parâmetros de corte e o valor da chapa: sem qualquer um deles não há como
     * ratear material nem precificar o comprimento cortado.
     */
    public boolean isChapaParametrizada() {
        return isPositivo(larguraMm)
                && isPositivo(comprimentoMm)
                && isPositivo(valorUnitario)
                && precoMetroCorte != null && precoMetroCorte.compareTo(BigDecimal.ZERO) >= 0;
    }

    private static boolean isPositivo(BigDecimal valor) {
        return valor != null && valor.compareTo(BigDecimal.ZERO) > 0;
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
