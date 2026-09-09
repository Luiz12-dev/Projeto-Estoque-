package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Produto;
import com.metalurgica.estoque.dto.response.ResumoCategoriaResponse;
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
            "LOWER(p.categoria.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')))")
    Page<Produto> buscar(@Param("termo") String termo, Pageable pageable);

    /**
     * Resumo por categoria para os blocos da tela de Produtos. Agrega no banco
     * de proposito: contar na tela contaria so a pagina carregada.
     * <p>
     * Categoria vazia aparece com zero itens -- e justamente o caso de quem
     * acabou de criar a prateleira e ainda vai enchê-la, entao o LEFT JOIN nao
     * pode virar INNER.
     */
    @Query("""
            SELECT new com.metalurgica.estoque.dto.response.ResumoCategoriaResponse(
                       c.id,
                       c.nome,
                       COUNT(p),
                       COALESCE(SUM(CASE WHEN p.quantidadeAtual < p.quantidadeMinima THEN 1L ELSE 0L END), 0L),
                       COALESCE(SUM(p.quantidadeAtual * p.valorUnitario), 0))
            FROM Categoria c LEFT JOIN Produto p ON p.categoria = c
            GROUP BY c.id, c.nome
            ORDER BY c.nome
            """)
    List<ResumoCategoriaResponse> resumoPorCategoria();

    /** Os itens sem prateleira, que a tela mostra num bloco proprio. */
    @Query("SELECT COUNT(p) FROM Produto p WHERE p.categoria IS NULL")
    long contarSemCategoria();

    @Query("""
            SELECT new com.metalurgica.estoque.dto.response.ResumoCategoriaResponse(
                       NULL, 'Sem categoria', COUNT(p),
                       COALESCE(SUM(CASE WHEN p.quantidadeAtual < p.quantidadeMinima THEN 1L ELSE 0L END), 0L),
                       COALESCE(SUM(p.quantidadeAtual * p.valorUnitario), 0))
            FROM Produto p WHERE p.categoria IS NULL
            """)
    ResumoCategoriaResponse resumoSemCategoria();

    Page<Produto> findByCategoriaId(Long categoriaId, Pageable pageable);

    Page<Produto> findByCategoriaIsNull(Pageable pageable);

    long countByCategoriaId(Long categoriaId);

    @Query("SELECT COALESCE(SUM(p.quantidadeAtual * p.valorUnitario), 0) FROM Produto p WHERE p.valorUnitario IS NOT NULL")
    BigDecimal calcularValorTotalEstoque();
}
