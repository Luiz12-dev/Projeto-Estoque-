package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.domain.enums.StatusOrdemServico;
import com.metalurgica.estoque.dto.request.OrdemServicoRequest;
import com.metalurgica.estoque.dto.request.OrdemServicoUpdateRequest;
import com.metalurgica.estoque.dto.response.MovimentacaoResponse;
import com.metalurgica.estoque.dto.response.OrdemServicoResponse;
import com.metalurgica.estoque.service.OrdemServicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;

    @PostMapping
    public ResponseEntity<OrdemServicoResponse> criar(@RequestBody @Valid OrdemServicoRequest request) {
        OrdemServicoResponse response = ordemServicoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<OrdemServicoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusOrdemServico status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(size = 20, sort = "data_abertura") Pageable pageable) {
        Page<OrdemServicoResponse> response = ordemServicoService.listar(
                busca, status,
                dataInicio != null ? dataInicio.atStartOfDay() : null,
                dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null,
                pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id) {
        OrdemServicoResponse response = ordemServicoService.buscarPorId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid OrdemServicoUpdateRequest request) {
        OrdemServicoResponse response = ordemServicoService.atualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/movimentacoes")
    public ResponseEntity<Page<MovimentacaoResponse>> listarMovimentacoes(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MovimentacaoResponse> response = ordemServicoService.listarMovimentacoes(id, pageable);
        return ResponseEntity.ok(response);
    }
}
