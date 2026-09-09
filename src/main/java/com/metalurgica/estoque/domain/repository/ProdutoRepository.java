package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Produto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca produto com lock pessimista (SELECT ... FOR UPDATE) para operações de estoque.
     * Serializa acessos concorrentes ao mesmo produto, prevenindo race conditions
     * de read-modify-write que causariam lost updates na quantidade em estoque.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.id = :id")
    Optional<Produto> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Produto p WHERE p.quantidadeAtual < p.quantidadeMinima")
    List<Produto> findEstoqueBaixo();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeAtual < p.quantidadeMinima")
    long countEstoqueBaixo();

    @Query("SELECT p FROM Produto p WHERE " +
            "CAST(:termo AS text) IS NULL OR " +
            "(LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) OR " +
            "LOWER(p.categoria) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')))")
    Page<Produto> buscar(@Param("termo") String termo, Pageable pageable);

    /**
     * Categorias já usadas, para o formulário sugerir em vez de deixar digitar
     * livre. Sem isso "disco" e "Discos de corte" viram dois grupos na
     * listagem, e a separação por categoria perde o sentido.
     */
    @Query("SELECT DISTINCT TRIM(p.categoria) FROM Produto p " +
            "WHERE p.categoria IS NOT NULL AND TRIM(p.categoria) <> '' " +
            "ORDER BY TRIM(p.categoria)")
    List<String> listarCategorias();

    @Query("SELECT COALESCE(SUM(p.quantidadeAtual * p.valorUnitario), 0) FROM Produto p WHERE p.valorUnitario IS NOT NULL")
    BigDecimal calcularValorTotalEstoque();
}
