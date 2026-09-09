package com.metalurgica.estoque.controller;

import java.util.List;

import com.metalurgica.estoque.dto.response.ResumoEmpresaOrcamentosResponse;

import com.metalurgica.estoque.domain.enums.SituacaoOrcamento;
import com.metalurgica.estoque.dto.request.OrcamentoRequest;
import com.metalurgica.estoque.dto.request.OrcamentoUpdateRequest;
import com.metalurgica.estoque.dto.request.SimulacaoOrcamentoRequest;
import com.metalurgica.estoque.dto.response.OrcamentoResponse;
import com.metalurgica.estoque.dto.response.SimulacaoOrcamentoResponse;
import com.metalurgica.estoque.service.OrcamentoService;
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
@RequestMapping("/api/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {

    private final OrcamentoService orcamentoService;

    /**
     * Calcula sem gravar. O frontend faz a mesma conta localmente para o preview
     * ser instantâneo; este endpoint é o contrato verificável entre os dois.
     */
    @PostMapping("/simular")
    public ResponseEntity<SimulacaoOrcamentoResponse> simular(
            @RequestBody @Valid SimulacaoOrcamentoRequest request) {
        return ResponseEntity.ok(orcamentoService.simular(request));
    }

    @PostMapping
    public ResponseEntity<OrcamentoResponse> criar(@RequestBody @Valid OrcamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orcamentoService.criar(request));
    }

    @GetMapping
    public ResponseEntity<Page<OrcamentoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) SituacaoOrcamento situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(size = 20, sort = "criadoEm") Pageable pageable) {

        return ResponseEntity.ok(orcamentoService.listar(
                busca,
                empresaId,
                situacao,
                dataInicio != null ? dataInicio.atStartOfDay() : null,
                dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null,
                pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrcamentoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(orcamentoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrcamentoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid OrcamentoUpdateRequest request) {
        return ResponseEntity.ok(orcamentoService.atualizar(id, request));
    }

    /** Blocos da tela: uma linha por empresa. */
    @GetMapping("/empresas/resumo")
    public ResponseEntity<List<ResumoEmpresaOrcamentosResponse>> resumoPorEmpresa() {
        return ResponseEntity.ok(orcamentoService.resumoPorEmpresa());
    }
}
