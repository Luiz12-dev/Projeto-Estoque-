package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    @Query("SELECT e FROM Empresa e WHERE " +
            "CAST(:termo AS text) IS NULL OR " +
            "(LOWER(e.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) OR " +
            "LOWER(e.cnpj) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')))")
    Page<Empresa> buscar(@Param("termo") String termo, Pageable pageable);
}
