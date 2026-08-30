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

    /** Casas decimais da coluna de estoque (produto.quantidade_atual é numeric(19,4)). */
    private static final int ESCALA_ESTOQUE = 4;

    /** Menor consumo que a coluna de estoque consegue representar. */
    private static final BigDecimal MENOR_FRACAO_DE_ESTOQUE = new BigDecimal("0.0001");

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
     * A peça cabe fisicamente na chapa?
     * <p>
     * Comparar apenas áreas não responde isso: uma peça de 1500 x 2000 mm tem
     * área menor que uma chapa de 1200 x 3000 mm, mas 1500 mm não cabe numa
     * chapa de 1200 mm de largura. O sistema orçaria uma peça impossível de
     * cortar, e quem descobre é o operador na máquina.
     * <p>
     * A peça pode entrar girada 90 graus — é o que se faz na prática quando a
     * peça é comprida e estreita —, então as duas orientações valem.
     */
    public boolean cabeNaChapa(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm) {

        return cabeNaOrientacao(larguraChapaMm, comprimentoChapaMm, larguraPecaMm, comprimentoPecaMm)
                || cabeNaOrientacao(larguraChapaMm, comprimentoChapaMm, comprimentoPecaMm, larguraPecaMm);
    }

    private static boolean cabeNaOrientacao(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal largura, BigDecimal comprimento) {
        return largura.compareTo(larguraChapaMm) <= 0 && comprimento.compareTo(comprimentoChapaMm) <= 0;
    }

    /**
     * Valida as medidas e explica o que está errado, com os números na frente
     * de quem lê. "Não cabe" sem dizer em que chapa obriga a pessoa a ir
     * conferir noutra tela.
     */
    private void validarMedidas(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm) {

        if (larguraChapaMm.multiply(comprimentoChapaMm).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("As dimensões da chapa devem ser maiores que zero.");
        }
        if (!cabeNaChapa(larguraChapaMm, comprimentoChapaMm, larguraPecaMm, comprimentoPecaMm)) {
            throw new IllegalArgumentException(String.format(
                    "A peça de %s x %s mm não cabe na chapa de %s x %s mm, nem girada.",
                    semZerosAtoa(larguraPecaMm), semZerosAtoa(comprimentoPecaMm),
                    semZerosAtoa(larguraChapaMm), semZerosAtoa(comprimentoChapaMm)));
        }
    }

    private static String semZerosAtoa(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
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

        validarMedidas(larguraChapaMm, comprimentoChapaMm, larguraPecaMm, comprimentoPecaMm);

        BigDecimal areaChapa = larguraChapaMm.multiply(comprimentoChapaMm);
        BigDecimal areaPeca = larguraPecaMm.multiply(comprimentoPecaMm);

        // Multiplica antes de dividir, com um único arredondamento no fim. Preserva
        // precisão e mantém o resultado idêntico ao da fórmula original do registro
        // de corte, que este método substituiu.
        return valorChapa.multiply(areaPeca).divide(areaChapa, ESCALA_MOEDA, RoundingMode.HALF_UP);
    }

    /**
     * Quanto de chapa é efetivamente consumido, em número de chapas.
     * <p>
     * É a mesma razão entre áreas que define o custo do material, e existe para
     * que estoque e custo parem de discordar: até aqui o custo dizia que a peça
     * valia 1% da chapa enquanto o estoque dava baixa de uma chapa inteira por
     * peça. Cortar duas peças pequenas consumia duas chapas do saldo.
     * <p>
     * O resultado sai na escala da coluna de estoque (quatro casas). Uma peça
     * pequena demais para essa escala consumiria zero, o que ao longo de muitos
     * cortes viraria material de graça — por isso o piso de uma unidade da menor
     * fração representável. Errar para mais é preferível a nunca dar baixa.
     */
    public BigDecimal calcularFracaoDaChapa(BigDecimal larguraChapaMm, BigDecimal comprimentoChapaMm,
            BigDecimal larguraPecaMm, BigDecimal comprimentoPecaMm, int quantidade) {

        validarMedidas(larguraChapaMm, comprimentoChapaMm, larguraPecaMm, comprimentoPecaMm);

        BigDecimal areaChapa = larguraChapaMm.multiply(comprimentoChapaMm);
        BigDecimal areaPeca = larguraPecaMm.multiply(comprimentoPecaMm);

        BigDecimal consumo = areaPeca.multiply(BigDecimal.valueOf(quantidade))
                .divide(areaChapa, ESCALA_ESTOQUE, RoundingMode.HALF_UP);

        return consumo.compareTo(BigDecimal.ZERO) > 0 ? consumo : MENOR_FRACAO_DE_ESTOQUE;
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
