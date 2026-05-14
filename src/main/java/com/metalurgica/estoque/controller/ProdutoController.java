package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.ProdutoRequest;
import com.metalurgica.estoque.dto.request.ProdutoUpdateRequest;
import com.metalurgica.estoque.dto.response.ProdutoResponse;
import com.metalurgica.estoque.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @PostMapping
    public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid ProdutoRequest request) {
        ProdutoResponse response = produtoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProdutoResponse>> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        Page<ProdutoResponse> response = produtoService.listar(busca, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponse> buscarPorId(@PathVariable Long id) {
        ProdutoResponse response = produtoService.buscarPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponse> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoUpdateRequest request) {
        ProdutoResponse response = produtoService.atualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/estoque-baixo")
    public ResponseEntity<List<ProdutoResponse>> listarEstoqueBaixo() {
        List<ProdutoResponse> response = produtoService.listarEstoqueBaixo();
        return ResponseEntity.ok(response);
    }
}
