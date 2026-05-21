package com.metalurgica.estoque.domain.entity;

import com.metalurgica.estoque.domain.enums.PrioridadeOrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordem_servico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, length = 150)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrdemServico status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrioridadeOrdemServico prioridade;

    @Column(name = "data_abertura", nullable = false, updatable = false)
    private LocalDateTime dataAbertura;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "ordemServico", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Movimentacao> movimentacoes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.dataAbertura = LocalDateTime.now();
        if (this.status == null) {
            this.status = StatusOrdemServico.ABERTA;
        }
        if (this.prioridade == null) {
            this.prioridade = PrioridadeOrdemServico.MEDIA;
        }
    }

    public boolean isAbertaOuEmAndamento() {
        return this.status == StatusOrdemServico.ABERTA || this.status == StatusOrdemServico.EM_ANDAMENTO;
    }
}
