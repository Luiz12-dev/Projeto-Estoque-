package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.PecaCortada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PecaCortadaRepository extends JpaRepository<PecaCortada, Long> {

    @Query("SELECT p FROM PecaCortada p " +
            "LEFT JOIN FETCH p.produto " +
            "LEFT JOIN FETCH p.usuario " +
            "WHERE p.ordemServico.id = :ordemServicoId " +
            "ORDER BY p.criadoEm DESC")
    List<PecaCortada> findByOrdemServicoId(Long ordemServicoId);
}
