package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.PecaCortada;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PecaCortadaRepository extends JpaRepository<PecaCortada, Long> {

    @Query("SELECT p FROM PecaCortada p " +
            "LEFT JOIN FETCH p.produto " +
            "LEFT JOIN FETCH p.usuario " +
            "LEFT JOIN FETCH p.ordemServico os " +
            "LEFT JOIN FETCH os.empresa " +
            "WHERE os.id = :ordemServicoId " +
            "ORDER BY p.criadoEm DESC")
    List<PecaCortada> findByOrdemServicoId(Long ordemServicoId);

    /**
     * Histórico consolidado de peças cortadas, filtrável por empresa e período.
     * <p>
     * A empresa é alcançada através da OS, então o JOIN FETCH desce até ela para
     * evitar N+1 ao montar a listagem — mesmo padrão das listagens de OS.
     */
    @Query(value = "SELECT p FROM PecaCortada p " +
            "LEFT JOIN FETCH p.produto " +
            "LEFT JOIN FETCH p.usuario " +
            "LEFT JOIN FETCH p.ordemServico os " +
            "LEFT JOIN FETCH os.empresa " +
            "WHERE (:empresaId IS NULL OR os.empresa.id = :empresaId) AND " +
            "(CAST(:termo AS text) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND " +
            "(CAST(:dataInicio AS text) IS NULL OR p.criadoEm >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND " +
            "(CAST(:dataFim AS text) IS NULL OR p.criadoEm <= CAST(CAST(:dataFim AS text) AS timestamp))",

            countQuery = "SELECT COUNT(p) FROM PecaCortada p WHERE " +
                    "(:empresaId IS NULL OR p.ordemServico.empresa.id = :empresaId) AND " +
                    "(CAST(:termo AS text) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND "
                    +
                    "(CAST(:dataInicio AS text) IS NULL OR p.criadoEm >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND "
                    +
                    "(CAST(:dataFim AS text) IS NULL OR p.criadoEm <= CAST(CAST(:dataFim AS text) AS timestamp))")
    Page<PecaCortada> buscar(@Param("termo") String termo,
            @Param("empresaId") Long empresaId,
            @Param("dataInicio") LocalDateTime dataInicio,
            @Param("dataFim") LocalDateTime dataFim,
            Pageable pageable);
}
