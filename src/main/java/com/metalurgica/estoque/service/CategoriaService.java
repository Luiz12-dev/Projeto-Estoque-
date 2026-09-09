package com.metalurgica.estoque.service;

import com.metalurgica.estoque.domain.entity.Categoria;
import com.metalurgica.estoque.domain.repository.CategoriaRepository;
import com.metalurgica.estoque.domain.repository.ProdutoRepository;
import com.metalurgica.estoque.dto.request.CategoriaRequest;
import com.metalurgica.estoque.dto.response.CategoriaResponse;
import com.metalurgica.estoque.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAllByOrderByNomeAsc().stream()
                .map(CategoriaResponse::fromEntity)
                .toList();
    }

    public CategoriaResponse criar(CategoriaRequest request) {
        String nome = request.nome().trim();
        if (categoriaRepository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Já existe uma categoria com esse nome");
        }
        Categoria salva = categoriaRepository.save(Categoria.builder().nome(nome).build());
        return CategoriaResponse.fromEntity(salva);
    }

    public CategoriaResponse renomear(Long id, CategoriaRequest request) {
        Categoria categoria = buscar(id);
        String nome = request.nome().trim();
        // Comparar ignorando caixa deixa passar "chapas" -> "Chapas", que é
        // arrumar a grafia da própria categoria, não colidir com outra.
        categoriaRepository.findByNomeIgnoreCase(nome)
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> { throw new IllegalArgumentException("Já existe uma categoria com esse nome"); });
        categoria.setNome(nome);
        return CategoriaResponse.fromEntity(categoria);
    }

    /**
     * Apagar categoria com item dentro é recusado com uma frase que diz o que
     * fazer. Sem isso o erro viria do banco, como violação de chave estrangeira,
     * que não significa nada para quem está na tela.
     */
    public void excluir(Long id) {
        Categoria categoria = buscar(id);
        long itens = produtoRepository.countByCategoriaId(id);
        if (itens > 0) {
            throw new IllegalArgumentException(
                    "A categoria \"" + categoria.getNome() + "\" tem " + itens
                            + (itens == 1 ? " item dentro" : " itens dentro")
                            + ". Mova ou apague os itens antes de excluí-la.");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
    }
}
