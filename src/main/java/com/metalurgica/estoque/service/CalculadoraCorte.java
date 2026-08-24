package com.metalurgica.estoque.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo de custo e preço de uma peça cortada a laser.
 * <p>
 * Fonte autoritativa da fórmula. O frontend reproduz a mesma conta para dar
 * retorno instantâneo enquanto o usuário digita, mas toda gravação recalcula
 * aqui — valor vindo do cliente nunca é confiado.
 * <p>
 * O preço tem duas parcelas. O <b>material</b> é a fração da chapa que a peça
 * ocupa. O <b>corte</b> é o quanto o laser precisa andar: o contorno da peça,
 * mais os furos, mais recortes internos informados à parte. Sem a parcela de
 * corte, uma peça lisa e uma peça cheia de furos custariam o mesmo, embora a
 * segunda ocupe muito mais tempo de máquina.
 */
@Component
public class CalculadoraCorte {

    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846");
    private static final BigDecimal MIL = new BigDecimal("1000");
    private static final BigDecimal CEM = new BigDecimal("100");

    /** Escala usada na razão entre áreas, antes de virar dinheiro. */
    private static final int ESCALA_FRACAO = 10;

    /** Toda quantia em reais é arredondada ao centavo. */
    private static final int ESCALA_MOEDA = 2;

    /**
     * Entrada do cálculo. Medidas em milímetros, valores em reais.
     * Os campos de corte interno são opcionais e aceitam null, tratado como zero
     * — quem corta apenas retângulos não informa nada além das medidas.
     */
    public record Entrada(
            BigDecimal larguraChapaMm,
            BigDecimal comprimentoChapaMm,
            BigDecimal valorChapa,
            BigDecimal precoMetroCorte,
            BigDecimal larguraPecaMm,
            BigDecimal comprimentoPecaMm,
            int quantidade,
            Integer quantidadeFuros,
            BigDecimal diametroFuroMm,
            BigDecimal corteExtraMetros,
            BigDecimal margemPercentual) {
    }

    /**
     * Resultado detalhado. As parcelas somam exatamente o subtotal, e o preço
     * total é sempre o preço unitário multiplicado pela quantidade — a conta
     * que o cliente confere no orçamento fecha em qualquer linha.
     */
    public record Resultado(
            BigDecimal comprimentoCorteMetros,
            BigDecimal custoMaterialUnitario,
            BigDecimal custoCorteUnitario,
            BigDecimal subtotalUnitario,
            BigDecimal precoUnitario,
            BigDecimal precoTotal) {
    }

    public Resultado calcular(Entrada e) {
        BigDecimal custoMaterial = calcularCustoMaterial(
                e.larguraChapaMm(), e.comprimentoChapaMm(), e.valorChapa(),
                e.larguraPecaMm(), e.comprimentoPecaMm());

        BigDecimal comprimentoCorteM = calcularComprimentoCorteMetros(
                e.larguraPecaMm(), e.comprimentoPecaMm(),
                e.quantidadeFuros(), e.diametroFuroMm(), e.corteExtraMetros());

        BigDecimal custoCorte = comprimentoCorteM
                .multiply(zeroSeNulo(e.precoMetroCorte()))
                .setScale(ESCALA_MOEDA, RoundingMode.HALF_UP);

        BigDecimal subtotal = custoMaterial.add(custoCorte);

        BigDecimal fatorMargem = BigDecimal.ONE.add(
                zeroSeNulo(e.margemPercentual()).divide(CEM, ESCALA_FRACAO, RoundingMode.HALF_UP));

        BigDecimal precoUnitario = subtotal.multiply(fatorMargem)
                .setScale(ESCALA_MOEDA, RoundingMode.HALF_UP);

        BigDecimal precoTotal = precoUnitario
                .multiply(BigDecimal.valueOf(e.quantidade()))
                .setScale(ESCALA_MOEDA, RoundingMode.HALF_UP);

        return new Resultado(
                comprimentoCorteM.setScale(4, RoundingMode.HALF_UP),
                custoMaterial,
                custoCorte,
                subtotal,
                precoUnitario,
                precoTotal);
    }

    /**
     * Rateio do material: a peça custa a fração da chapa que a sua área ocupa.
     * <p>
     * Este é o mesmo cálculo usado no registro de um corte realizado, onde
     * entra sozinho — sem parcela de corte e sem margem, porque ali o valor
     * alimenta uma movimentação de estoque, e tempo de máquina não é estoque.
     */
    public BigDecimal calcularCustoMaterial(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal valorChapa, BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm) {

        BigDecimal areaChapa = larguraChapaMm.multiply(comprimentoChapaMm);
        BigDecimal areaPeca = larguraPecaMm.multiply(comprimentoPecaMm);

        if (areaChapa.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("As dimensões da chapa devem ser maiores que zero.");
        }
        if (areaPeca.compareTo(areaChapa) > 0) {
            throw new IllegalArgumentException("As dimensões da peça não podem ser maiores que as da chapa.");
        }

        // Multiplica antes de dividir, com um único arredondamento no fim. Preserva
        // precisão e mantém o resultado idêntico ao da fórmula original do registro
        // de corte, que este método substituiu.
        return valorChapa.multiply(areaPeca).divide(areaChapa, ESCALA_MOEDA, RoundingMode.HALF_UP);
    }

    /**
     * Quanto o laser percorre para produzir uma peça: o contorno externo, mais
     * a circunferência de cada furo, mais recortes internos informados em metros.
     */
    public BigDecimal calcularComprimentoCorteMetros(BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm,
            Integer quantidadeFuros, BigDecimal diametroFuroMm, BigDecimal corteExtraMetros) {

        BigDecimal perimetroMm = larguraPecaMm.add(comprimentoPecaMm).multiply(BigDecimal.valueOf(2));

        int furos = quantidadeFuros != null ? quantidadeFuros : 0;
        BigDecimal furosMm = furos <= 0
                ? BigDecimal.ZERO
                : PI.multiply(zeroSeNulo(diametroFuroMm)).multiply(BigDecimal.valueOf(furos));

        BigDecimal extraMm = zeroSeNulo(corteExtraMetros).multiply(MIL);

        // movePointLeft(3) divide por mil de forma exata, sem introduzir arredondamento
        return perimetroMm.add(furosMm).add(extraMm).movePointLeft(3);
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
