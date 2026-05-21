package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

    Optional<OrdemServico> findByCodigo(String codigo);

    @Query(value = "SELECT * FROM ordem_servico os WHERE " +
            "(:termo IS NULL OR LOWER(os.codigo) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%') " +
            "OR LOWER(os.descricao) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%') " +
            "OR LOWER(os.cliente) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%')) AND " +
            "(:status IS NULL OR os.status = CAST(:status AS TEXT)) AND " +
            "(CAST(:dataInicio AS TIMESTAMP) IS NULL OR os.data_abertura >= CAST(:dataInicio AS TIMESTAMP)) AND " +
            "(CAST(:dataFim AS TIMESTAMP) IS NULL OR os.data_abertura <= CAST(:dataFim AS TIMESTAMP))",
            countQuery = "SELECT COUNT(*) FROM ordem_servico os WHERE " +
            "(:termo IS NULL OR LOWER(os.codigo) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%') " +
            "OR LOWER(os.descricao) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%') " +
            "OR LOWER(os.cliente) LIKE LOWER('%' || CAST(:termo AS TEXT) || '%')) AND " +
            "(:status IS NULL OR os.status = CAST(:status AS TEXT)) AND " +
            "(CAST(:dataInicio AS TIMESTAMP) IS NULL OR os.data_abertura >= CAST(:dataInicio AS TIMESTAMP)) AND " +
            "(CAST(:dataFim AS TIMESTAMP) IS NULL OR os.data_abertura <= CAST(:dataFim AS TIMESTAMP))",
            nativeQuery = true)
    Page<OrdemServico> buscar(@Param("termo") String termo,
                              @Param("status") String status,
                              @Param("dataInicio") java.time.LocalDateTime dataInicio,
                              @Param("dataFim") java.time.LocalDateTime dataFim,
                              Pageable pageable);

    long countByStatus(StatusOrdemServico status);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(os.codigo, 4) AS int)), 0) FROM OrdemServico os")
    int findMaxCodigo();
}
