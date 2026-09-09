package com.metalurgica.estoque.domain.repository;

import java.util.List;

import com.metalurgica.estoque.dto.response.ResumoEmpresaOrcamentosResponse;

import com.metalurgica.estoque.domain.entity.Orcamento;
import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    /**
     * Busca com filtros compostos, resolvidos no banco. Segue o mesmo padrão de
     * cast duplo usado em OrdemServicoRepository para lidar com nulls em datas
     * no PostgreSQL.
     */
    @Query(value = "SELECT o FROM Orcamento o LEFT JOIN FETCH o.empresa LEFT JOIN FETCH o.usuario WHERE " +
            "(CAST(:termo AS text) IS NULL OR LOWER(o.codigo) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) " +
            "OR LOWER(o.empresa.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND " +
            "(:empresaId IS NULL OR o.empresa.id = :empresaId) AND " +
            "(:situacao IS NULL OR o.situacao = :situacao) AND " +
            "(CAST(:dataInicio AS text) IS NULL OR o.criadoEm >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND " +
            "(CAST(:dataFim AS text) IS NULL OR o.criadoEm <= CAST(CAST(:dataFim AS text) AS timestamp))",

            countQuery = "SELECT COUNT(o) FROM Orcamento o WHERE " +
                    "(CAST(:termo AS text) IS NULL OR LOWER(o.codigo) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) "
                    +
                    "OR LOWER(o.empresa.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND " +
                    "(:empresaId IS NULL OR o.empresa.id = :empresaId) AND " +
                    "(:situacao IS NULL OR o.situacao = :situacao) AND " +
                    "(CAST(:dataInicio AS text) IS NULL OR o.criadoEm >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND "
                    +
                    "(CAST(:dataFim AS text) IS NULL OR o.criadoEm <= CAST(CAST(:dataFim AS text) AS timestamp))")
    Page<Orcamento> buscar(@Param("termo") String termo,
            @Param("empresaId") Long empresaId,
            @Param("situacao") SituacaoOrcamento situacao,
            @Param("dataInicio") LocalDateTime dataInicio,
            @Param("dataFim") LocalDateTime dataFim,
            Pageable pageable);

    /** Detalhe com itens e produtos, em uma consulta só, evitando N+1. */
    @Query("SELECT o FROM Orcamento o " +
            "LEFT JOIN FETCH o.empresa " +
            "LEFT JOIN FETCH o.usuario " +
            "LEFT JOIN FETCH o.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE o.id = :id")
    Optional<Orcamento> buscarComItens(@Param("id") Long id);

    /**
     * Próximo valor da sequence do código, no mesmo padrão da OS.
     * Atômico, então dois orçamentos simultâneos nunca recebem o mesmo código.
     */
    @Query(value = "SELECT nextval('orcamento_codigo_seq')", nativeQuery = true)
    long getNextCodigoSequence();

    /** Blocos da aba de orcamentos, por empresa. */
    @Query("""
            SELECT new com.metalurgica.estoque.dto.response.ResumoEmpresaOrcamentosResponse(
                       e.id, e.nome, COUNT(o),
                       COALESCE(SUM(CASE WHEN o.situacao = com.metalurgica.estoque.domain.enums.SituacaoOrcamento.PENDENTE
                                         THEN 1L ELSE 0L END), 0L),
                       COALESCE(SUM(o.valorTotal), 0))
            FROM Orcamento o JOIN o.empresa e
            GROUP BY e.id, e.nome
            ORDER BY e.nome
            """)
    List<ResumoEmpresaOrcamentosResponse> resumoPorEmpresa();
}
