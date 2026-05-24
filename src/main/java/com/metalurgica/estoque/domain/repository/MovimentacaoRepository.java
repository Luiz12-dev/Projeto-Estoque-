package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Movimentacao;
import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

       List<Movimentacao> findTop5ByOrderByDataHoraDesc();

       long countByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim);

       long countByOrdemServicoId(Long ordemServicoId);

       @Query("SELECT COALESCE(SUM(m.quantidade * m.valorUnitario), 0) " +
                     "FROM Movimentacao m " +
                     "WHERE m.tipo = :tipo")
       BigDecimal somarValorTotalPorTipo(@Param("tipo") TipoMovimentacao tipo);

       @Query(value = "SELECT m FROM Movimentacao m " +
                     "JOIN FETCH m.produto p " +
                     "JOIN FETCH m.usuario u " +
                     "WHERE (:termo IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%'))) " +
                     "AND (:tipo IS NULL OR m.tipo = :tipo)", countQuery = "SELECT COUNT(m) FROM Movimentacao m " +
                                   "JOIN m.produto p " +
                                   "WHERE (:termo IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%'))) " +
                                   "AND (:tipo IS NULL OR m.tipo = :tipo)")
       Page<Movimentacao> buscar(
                     @Param("termo") String termo,
                     @Param("tipo") TipoMovimentacao tipo,
                     Pageable pageable);

       Page<Movimentacao> findByProdutoIdOrderByDataHoraDesc(Long produtoId, Pageable pageable);

       @Query("SELECT m.ordemServico.id, COALESCE(SUM(m.quantidade * m.valorUnitario), 0) " +
                     "FROM Movimentacao m " +
                     "WHERE m.ordemServico.id IN :osIds " +
                     "GROUP BY m.ordemServico.id")
       List<Object[]> somarCustosPorOsIds(@Param("osIds") List<Long> osIds);

       @Query("SELECT m.ordemServico.id, COUNT(m) " +
                     "FROM Movimentacao m " +
                     "WHERE m.ordemServico.id IN :osIds " +
                     "GROUP BY m.ordemServico.id")
       List<Object[]> contarPorOsIds(@Param("osIds") List<Long> osIds);

       @Query("SELECT m.id " +
                     "FROM Movimentacao m " +
                     "WHERE m.ordemServico.id = :osId " +
                     "ORDER BY m.dataHora DESC")
       Page<Long> buscarIdsPorOrdemServicoId(@Param("osId") Long osId, Pageable pageable);

       @Query("SELECT m FROM Movimentacao m " +
                     "JOIN FETCH m.produto " +
                     "JOIN FETCH m.usuario " +
                     "WHERE m.id IN :ids " +
                     "ORDER BY m.dataHora DESC")
       List<Movimentacao> findByIdsComFetch(@Param("ids") List<Long> ids);

       @Query("SELECT COALESCE(SUM(m.quantidade * m.valorUnitario), 0) " +
                     "FROM Movimentacao m " +
                     "WHERE m.ordemServico.id = :osId")
       BigDecimal somarCustoPorOrdemServico(@Param("osId") Long osId);
}
