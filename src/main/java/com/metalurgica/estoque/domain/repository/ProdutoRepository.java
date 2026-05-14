package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    @Query("SELECT p FROM Produto p WHERE p.quantidadeAtual < p.quantidadeMinima")
    List<Produto> findEstoqueBaixo();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeAtual < p.quantidadeMinima")
    long countEstoqueBaixo();

    @Query("SELECT p FROM Produto p WHERE " +
           "LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
           "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))")
    Page<Produto> buscar(@Param("termo") String termo, Pageable pageable);
}
