package com.metalurgica.estoque.domain.entity;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários puros para a lógica de domínio da entidade OrdemServico.
 * Sem Spring Context.
 */
class OrdemServicoTest {

    @Nested
    @DisplayName("isAbertaOuEmAndamento()")
    class IsAbertaOuEmAndamento {

        @Test
        @DisplayName("Deve retornar true quando status é ABERTA")
        void deveRetornarTrueParaAberta() {
            // Arrange
            OrdemServico os = OrdemServico.builder()
                    .status(StatusOrdemServico.ABERTA)
                    .build();

            // Act & Assert
            assertThat(os.isAbertaOuEmAndamento()).isTrue();
        }

        @Test
        @DisplayName("Deve retornar true quando status é EM_ANDAMENTO")
        void deveRetornarTrueParaEmAndamento() {
            // Arrange
            OrdemServico os = OrdemServico.builder()
                    .status(StatusOrdemServico.EM_ANDAMENTO)
                    .build();

            // Act & Assert
            assertThat(os.isAbertaOuEmAndamento()).isTrue();
        }

        @Test
        @DisplayName("Deve retornar false quando status é CONCLUIDA")
        void deveRetornarFalseParaConcluida() {
            // Arrange
            OrdemServico os = OrdemServico.builder()
                    .status(StatusOrdemServico.CONCLUIDA)
                    .build();

            // Act & Assert
            assertThat(os.isAbertaOuEmAndamento()).isFalse();
        }

        @Test
        @DisplayName("Deve retornar false quando status é CANCELADA")
        void deveRetornarFalseParaCancelada() {
            // Arrange
            OrdemServico os = OrdemServico.builder()
                    .status(StatusOrdemServico.CANCELADA)
                    .build();

            // Act & Assert
            assertThat(os.isAbertaOuEmAndamento()).isFalse();
        }
    }

    @Nested
    @DisplayName("@PrePersist — onCreate()")
    class OnCreate {

        @Test
        @DisplayName("Deve definir status ABERTA e prioridade MEDIA quando nulos no @PrePersist")
        void deveDefinirDefaultsNoPrePersist() {
            // Arrange
            OrdemServico os = OrdemServico.builder().build();

            // Act — simula o callback JPA
            os.onCreate();

            // Assert
            assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.ABERTA);
            assertThat(os.getPrioridade()).isEqualTo(PrioridadeOrdemServico.MEDIA);
            assertThat(os.getDataAbertura()).isNotNull();
        }

        @Test
        @DisplayName("Deve manter status e prioridade quando já definidos antes do @PrePersist")
        void deveManterValoresExistentesNoPrePersist() {
            // Arrange
            OrdemServico os = OrdemServico.builder()
                    .status(StatusOrdemServico.EM_ANDAMENTO)
                    .prioridade(PrioridadeOrdemServico.URGENTE)
                    .build();

            // Act
            os.onCreate();

            // Assert
            assertThat(os.getStatus()).isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
            assertThat(os.getPrioridade()).isEqualTo(PrioridadeOrdemServico.URGENTE);
        }
    }
}
