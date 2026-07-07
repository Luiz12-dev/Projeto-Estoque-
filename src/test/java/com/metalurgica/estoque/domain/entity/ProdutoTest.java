package com.metalurgica.estoque.domain.entity;

import com.metalurgica.estoque.exception.EstoqueInsuficienteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários puros para a lógica de domínio da entidade Produto.
 * Sem Spring Context — testa apenas regras de negócio encapsuladas na entidade.
 */
class ProdutoTest {

    // ============================
    // Factory Method — DRY
    // ============================

    private Produto criarProduto(String quantidade, String minimo) {
        return Produto.builder()
                .id(1L)
                .nome("Chapa Aço 3mm")
                .quantidadeAtual(new BigDecimal(quantidade))
                .quantidadeMinima(new BigDecimal(minimo))
                .unidadeMedida("UN")
                .build();
    }

    @Nested
    @DisplayName("adicionarEstoque()")
    class AdicionarEstoque {

        @Test
        @DisplayName("Deve somar a quantidade informada ao estoque atual")
        void deveSomarQuantidade() {
            // Arrange
            Produto produto = criarProduto("10.00", "5.00");

            // Act
            produto.adicionarEstoque(new BigDecimal("5.00"));

            // Assert
            assertThat(produto.getQuantidadeAtual()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("Deve funcionar com valores decimais fracionados")
        void deveSomarValoresDecimais() {
            // Arrange
            Produto produto = criarProduto("10.50", "5.00");

            // Act
            produto.adicionarEstoque(new BigDecimal("0.75"));

            // Assert
            assertThat(produto.getQuantidadeAtual()).isEqualByComparingTo("11.25");
        }
    }

    @Nested
    @DisplayName("baixarEstoque()")
    class BaixarEstoque {

        @Test
        @DisplayName("Deve subtrair a quantidade informada do estoque atual")
        void deveSubtrairQuantidade() {
            // Arrange
            Produto produto = criarProduto("10.00", "5.00");

            // Act
            produto.baixarEstoque(new BigDecimal("3.00"));

            // Assert
            assertThat(produto.getQuantidadeAtual()).isEqualByComparingTo("7.00");
        }

        @Test
        @DisplayName("Deve permitir baixar exatamente todo o estoque (resultado = 0)")
        void devePermitirZerarEstoque() {
            // Arrange
            Produto produto = criarProduto("5.00", "2.00");

            // Act
            produto.baixarEstoque(new BigDecimal("5.00"));

            // Assert
            assertThat(produto.getQuantidadeAtual()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Deve lançar EstoqueInsuficienteException quando a saída excede o estoque")
        void deveLancarExcecaoQuandoEstoqueInsuficiente() {
            // Arrange
            Produto produto = criarProduto("5.00", "2.00");

            // Act & Assert
            assertThatThrownBy(() -> produto.baixarEstoque(new BigDecimal("10.00")))
                    .isInstanceOf(EstoqueInsuficienteException.class)
                    .hasMessageContaining("Chapa Aço 3mm")
                    .hasMessageContaining("5");
        }

        @Test
        @DisplayName("Deve lançar exceção mesmo para diferenças fracionárias (5.00 - 5.01)")
        void deveLancarExcecaoParaDiferencaFracionaria() {
            // Arrange
            Produto produto = criarProduto("5.00", "2.00");

            // Act & Assert
            assertThatThrownBy(() -> produto.baixarEstoque(new BigDecimal("5.01")))
                    .isInstanceOf(EstoqueInsuficienteException.class);
        }
    }

    @Nested
    @DisplayName("isEstoqueBaixo()")
    class IsEstoqueBaixo {

        @Test
        @DisplayName("Deve retornar true quando estoque atual está abaixo do mínimo")
        void deveRetornarTrueQuandoAbaixoDoMinimo() {
            // Arrange
            Produto produto = criarProduto("3.00", "5.00");

            // Act & Assert
            assertThat(produto.isEstoqueBaixo()).isTrue();
        }

        @Test
        @DisplayName("Deve retornar false quando estoque atual está igual ao mínimo")
        void deveRetornarFalseQuandoIgualAoMinimo() {
            // Arrange
            Produto produto = criarProduto("5.00", "5.00");

            // Act & Assert
            assertThat(produto.isEstoqueBaixo()).isFalse();
        }

        @Test
        @DisplayName("Deve retornar false quando estoque atual está acima do mínimo")
        void deveRetornarFalseQuandoAcimaDoMinimo() {
            // Arrange
            Produto produto = criarProduto("10.00", "5.00");

            // Act & Assert
            assertThat(produto.isEstoqueBaixo()).isFalse();
        }
    }
}
