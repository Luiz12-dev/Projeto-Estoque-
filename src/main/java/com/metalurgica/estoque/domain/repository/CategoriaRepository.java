package com.metalurgica.estoque.domain.repository;

import com.metalurgica.estoque.domain.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findAllByOrderByNomeAsc();

    /** "Chapas" e "chapas" são a mesma prateleira. */
    Optional<Categoria> findByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCase(String nome);
}
