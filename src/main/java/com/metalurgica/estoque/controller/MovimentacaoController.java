package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.TipoMovimentacao;
import com.metalurgica.estoque.dto.request.MovimentacaoRequest;
import com.metalurgica.estoque.dto.request.MovimentacaoUpdateRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.ResumoMovimentacaoCategoriaResponse;
import com.metalurgica.estoque.service.MovimentacaoService;
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
@RequestMapping("/api/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;

    @PostMapping
    public ResponseEntity<MovimentacaoResponse> registrar(@RequestBody @Valid MovimentacaoRequest request) {
        MovimentacaoResponse response = movimentacaoService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MovimentacaoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid MovimentacaoUpdateRequest request) {
        MovimentacaoResponse response = movimentacaoService.atualizar(id, request);
        return ResponseEntity.ok(response);
    }

    /** Blocos da tela: uma linha por categoria de produto. */
    @GetMapping("/categorias/resumo")
    public ResponseEntity<List<ResumoMovimentacaoCategoriaResponse>> resumoPorCategoria() {
        return ResponseEntity.ok(movimentacaoService.resumoPorCategoria());
    }

    @GetMapping
    public ResponseEntity<Page<MovimentacaoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false, defaultValue = "false") boolean semCategoria,
            @PageableDefault(size = 20, sort = "dataHora") Pageable pageable) {

        Page<MovimentacaoResponse> response;
        if (categoriaId != null || semCategoria) {
            response = movimentacaoService.listarPorCategoria(categoriaId, semCategoria, pageable);
        } else if (busca != null || tipo != null) {
            response = movimentacaoService.buscar(busca, tipo, pageable);
        } else {
            response = movimentacaoService.listarTodas(pageable);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<Page<MovimentacaoResponse>> listarPorProduto(
            @PathVariable Long produtoId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MovimentacaoResponse> response = movimentacaoService.listarPorProduto(produtoId, pageable);
        return ResponseEntity.ok(response);
    }
}
