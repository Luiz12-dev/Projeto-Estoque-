package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    Page<Movimentacao> findByProdutoIdOrderByDataHoraDesc(Long produtoId, Pageable pageable);

    List<Movimentacao> findTop5ByOrderByDataHoraDesc();

    long countByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(m.quantidade * m.valorUnitario), 0) FROM Movimentacao m WHERE m.tipo = :tipo")
    BigDecimal somarValorTotalPorTipo(TipoMovimentacao tipo);
}
