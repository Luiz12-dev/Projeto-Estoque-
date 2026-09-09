package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.CategoriaRequest;
import com.metalurgica.estoque.dto.response.CategoriaResponse;
import com.metalurgica.estoque.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> listar() {
        return ResponseEntity.ok(categoriaService.listar());
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@RequestBody @Valid CategoriaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponse> renomear(@PathVariable Long id,
                                                      @RequestBody @Valid CategoriaRequest request) {
        return ResponseEntity.ok(categoriaService.renomear(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
