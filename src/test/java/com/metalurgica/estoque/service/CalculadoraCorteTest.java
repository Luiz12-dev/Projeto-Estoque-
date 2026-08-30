package com.metalurgica.estoque.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercita a fórmula de corte contra a tabela de casos compartilhada com o
 * frontend (casos-corte.json). A tabela é a mesma dos dois lados: se as duas
 * implementações divergirem, um dos lados falha aqui.
 */
class CalculadoraCorteTest {

    private final CalculadoraCorte calculadora = new CalculadoraCorte();

    // ---------- carga da tabela compartilhada ----------

    private static JsonNode tabela() {
        try (InputStream in = CalculadoraCorteTest.class.getResourceAsStream("/casos-corte.json")) {
            assertThat(in).as("casos-corte.json deve estar em src/test/resources").isNotNull();
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a tabela de casos compartilhada", e);
        }
    }

    static List<Caso> casosDeSucesso() {
        List<Caso> casos = new ArrayList<>();
        tabela().get("casos").forEach(no -> casos.add(new Caso(no.get("nome").asText(), no)));
        return casos;
    }

    static List<Caso> casosDeErro() {
        List<Caso> casos = new ArrayList<>();
        tabela().get("casosDeErro").forEach(no -> casos.add(new Caso(no.get("nome").asText(), no)));
        return casos;
    }

    /** Envelope só para o JUnit exibir o nome do caso no relatório. */
    record Caso(String nome, JsonNode no) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static CalculadoraCorte.Entrada entradaDe(JsonNode caso) {
        JsonNode e = caso.get("entrada");
        return new CalculadoraCorte.Entrada(
                dec(e, "larguraChapaMm"),
                dec(e, "comprimentoChapaMm"),
                dec(e, "valorChapa"),
                dec(e, "precoMetroCorte"),
                dec(e, "larguraPecaMm"),
                dec(e, "comprimentoPecaMm"),
                e.get("quantidade").asInt(),
                e.get("quantidadeFuros").asInt(),
                dec(e, "diametroFuroMm"),
                dec(e, "corteExtraMetros"),
                dec(e, "margemPercentual"));
    }

    private static BigDecimal dec(JsonNode no, String campo) {
        return new BigDecimal(no.get(campo).asText());
    }

    // ---------- a tabela compartilhada ----------

    @Nested
    @DisplayName("tabela compartilhada com o frontend")
    class TabelaCompartilhada {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.metalurgica.estoque.service.CalculadoraCorteTest#casosDeSucesso")
        @DisplayName("produz exatamente os valores da tabela")
        void deveBaterComATabela(Caso caso) {
            JsonNode esperado = caso.no().get("esperado");

            CalculadoraCorte.Resultado r = calculadora.calcular(entradaDe(caso.no()));

            assertThat(r.comprimentoCorteMetros())
                    .as("comprimento de corte")
                    .isEqualByComparingTo(esperado.get("comprimentoCorteMetros").asText());
            assertThat(r.custoMaterialUnitario())
                    .as("custo de material")
                    .isEqualByComparingTo(esperado.get("custoMaterialUnitario").asText());
            assertThat(r.custoCorteUnitario())
                    .as("custo de corte")
                    .isEqualByComparingTo(esperado.get("custoCorteUnitario").asText());
            assertThat(r.subtotalUnitario())
                    .as("subtotal")
                    .isEqualByComparingTo(esperado.get("subtotalUnitario").asText());
            assertThat(r.precoUnitario())
                    .as("preço unitário")
                    .isEqualByComparingTo(esperado.get("precoUnitario").asText());
            assertThat(r.precoTotal())
                    .as("preço total")
                    .isEqualByComparingTo(esperado.get("precoTotal").asText());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.metalurgica.estoque.service.CalculadoraCorteTest#casosDeErro")
        @DisplayName("rejeita as entradas inválidas da tabela")
        void deveRejeitarEntradasInvalidas(Caso caso) {
            String trecho = caso.no().get("mensagemContem").asText();

            assertThatThrownBy(() -> calculadora.calcular(entradaDe(caso.no())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(trecho);
        }
    }

    // ---------- invariantes que a tabela não expressa ----------

    @Nested
    @DisplayName("invariantes do cálculo")
    class Invariantes {

        @Test
        @DisplayName("as parcelas somam exatamente o subtotal, para o detalhamento fechar na tela")
        void parcelasDevemSomarOSubtotal() {
            CalculadoraCorte.Resultado r = calculadora.calcular(new CalculadoraCorte.Entrada(
                    new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("487.33"),
                    new BigDecimal("13.77"),
                    new BigDecimal("237"), new BigDecimal("113"),
                    1, 7, new BigDecimal("8.5"), new BigDecimal("0.375"),
                    new BigDecimal("17.5")));

            assertThat(r.custoMaterialUnitario().add(r.custoCorteUnitario()))
                    .isEqualByComparingTo(r.subtotalUnitario());
        }

        @Test
        @DisplayName("o total é sempre o unitário multiplicado pela quantidade")
        void totalDeveSerUnitarioVezesQuantidade() {
            CalculadoraCorte.Resultado r = calculadora.calcular(new CalculadoraCorte.Entrada(
                    new BigDecimal("1250"), new BigDecimal("2500"), new BigDecimal("819.99"),
                    new BigDecimal("7.31"),
                    new BigDecimal("333"), new BigDecimal("177"),
                    37, 3, new BigDecimal("12"), new BigDecimal("0"),
                    new BigDecimal("22.5")));

            assertThat(r.precoTotal())
                    .isEqualByComparingTo(r.precoUnitario().multiply(BigDecimal.valueOf(37)));
        }

        @Test
        @DisplayName("campos opcionais de corte interno aceitam null e valem zero")
        void camposOpcionaisAceitamNull() {
            CalculadoraCorte.Resultado comNulls = calculadora.calcular(new CalculadoraCorte.Entrada(
                    new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("200"), new BigDecimal("100"),
                    1, null, null, null, null));

            // Equivale ao caso "com preço por metro de corte", sem furos e sem margem
            assertThat(comNulls.precoUnitario()).isEqualByComparingTo("11.00");
        }

        @Test
        @DisplayName("margem zero não altera o subtotal")
        void margemZeroNaoAlteraSubtotal() {
            CalculadoraCorte.Resultado r = calculadora.calcular(new CalculadoraCorte.Entrada(
                    new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("200"), new BigDecimal("100"),
                    1, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

            assertThat(r.precoUnitario()).isEqualByComparingTo(r.subtotalUnitario());
        }
    }

    // ---------- custo de material isolado, reaproveitado pelo registro de corte ----------

    @Nested
    @DisplayName("custo de material isolado")
    class CustoMaterial {

        @Test
        @DisplayName("mantém o rateio proporcional usado no registro de corte")
        void deveManterRateioProporcional() {
            BigDecimal custo = calculadora.calcularCustoMaterial(
                    new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                    new BigDecimal("200"), new BigDecimal("100"));

            assertThat(custo).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("rejeita peça maior que a chapa")
        void deveRejeitarPecaMaiorQueChapa() {
            assertThatThrownBy(() -> calculadora.calcularCustoMaterial(
                    new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500.00"),
                    new BigDecimal("1500"), new BigDecimal("2000")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não cabe");
        }

        @Test
        @DisplayName("a mensagem mostra as medidas, para não obrigar a conferir noutra tela")
        void mensagemMostraAsMedidas() {
            assertThatThrownBy(() -> calculadora.calcularCustoMaterial(
                    new BigDecimal("1200"), new BigDecimal("3000"), new BigDecimal("695.00"),
                    new BigDecimal("1500"), new BigDecimal("2000")))
                    .hasMessageContaining("1500 x 2000")
                    .hasMessageContaining("1200 x 3000");
        }
    }

    // ---------- encaixe físico da peça na chapa ----------

    @Nested
    @DisplayName("encaixe na chapa")
    class Encaixe {

        private static final BigDecimal LARGURA_CHAPA = new BigDecimal("1200");
        private static final BigDecimal COMPRIMENTO_CHAPA = new BigDecimal("3000");

        private boolean cabe(String largura, String comprimento) {
            return calculadora.cabeNaChapa(LARGURA_CHAPA, COMPRIMENTO_CHAPA,
                    new BigDecimal(largura), new BigDecimal(comprimento));
        }

        @Test
        @DisplayName("peça menor nos dois lados cabe")
        void pecaMenorCabe() {
            assertThat(cabe("200", "100")).isTrue();
        }

        @Test
        @DisplayName("peça do tamanho exato da chapa cabe")
        void pecaExataCabe() {
            assertThat(cabe("1200", "3000")).isTrue();
        }

        @Test
        @DisplayName("peça comprida cabe se girada 90 graus")
        void pecaCompridaCabeGirada() {
            // 2800 nao entra nos 1200 de largura, mas entra nos 3000 de
            // comprimento. Girar a peça é o que se faz na máquina.
            assertThat(cabe("2800", "1000")).isTrue();
        }

        @Test
        @DisplayName("REGRESSÃO: peça que cabe por área mas não fisicamente é recusada")
        void pecaQueCabePorAreaMasNaoFisicamente() {
            // 1500 x 2000 = 3.000.000 mm², menos que os 3.600.000 mm² da chapa.
            // Uma validação por área aceitaria, e o sistema orçaria uma peça
            // impossível de cortar. Nem girada ela entra: 1500 > 1200 numa
            // orientação, 2000 > 1200 na outra.
            assertThat(cabe("1500", "2000")).isFalse();
        }

        @Test
        @DisplayName("um milímetro além da largura já não cabe")
        void umMilimetroAlemNaoCabe() {
            assertThat(cabe("1201", "2999")).isFalse();
        }

        @Test
        @DisplayName("peça mais comprida que o maior lado não cabe de jeito nenhum")
        void maisCompridaQueOMaiorLadoNaoCabe() {
            assertThat(cabe("3001", "10")).isFalse();
        }
    }
}
