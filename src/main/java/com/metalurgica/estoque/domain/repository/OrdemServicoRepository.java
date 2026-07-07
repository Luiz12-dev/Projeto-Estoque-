package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

    Optional<OrdemServico> findByCodigo(String codigo);

    /**
     * Busca com filtros usando JPQL blindada contra erros de tipagem do PostgreSQL.
     * Faz LEFT JOIN FETCH do usuario para evitar LazyInitializationException.
     * Utiliza Duplo Cast nas datas (text -> timestamp) para resolver o problema do
     * 'bytea' com nulls.
     */
    @Query(value = "SELECT os FROM OrdemServico os LEFT JOIN FETCH os.usuario WHERE " +
            "(CAST(:termo AS text) IS NULL OR LOWER(os.codigo) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) " +
            "OR LOWER(os.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) " +
            "OR LOWER(os.cliente) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND " +
            "(:status IS NULL OR os.status = :status) AND " +
            "(CAST(:dataInicio AS text) IS NULL OR os.dataAbertura >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND "
            +
            "(CAST(:dataFim AS text) IS NULL OR os.dataAbertura <= CAST(CAST(:dataFim AS text) AS timestamp))",

            countQuery = "SELECT COUNT(os) FROM OrdemServico os WHERE " +
                    "(CAST(:termo AS text) IS NULL OR LOWER(os.codigo) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) "
                    +
                    "OR LOWER(os.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) " +
                    "OR LOWER(os.cliente) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) AND " +
                    "(:status IS NULL OR os.status = :status) AND " +
                    "(CAST(:dataInicio AS text) IS NULL OR os.dataAbertura >= CAST(CAST(:dataInicio AS text) AS timestamp)) AND "
                    +
                    "(CAST(:dataFim AS text) IS NULL OR os.dataAbertura <= CAST(CAST(:dataFim AS text) AS timestamp))")
    Page<OrdemServico> buscar(@Param("termo") String termo,
            @Param("status") StatusOrdemServico status,
            @Param("dataInicio") LocalDateTime dataInicio,
            @Param("dataFim") LocalDateTime dataFim,
            Pageable pageable);

    long countByStatus(StatusOrdemServico status);

    /**
     * Busca o próximo valor da sequence para gerar o código da OS.
     * Usa sequence do PostgreSQL para garantir unicidade em concorrência.
     */
    @Query(value = "SELECT nextval('os_codigo_seq')", nativeQuery = true)
    long getNextCodigoSequence();
}
