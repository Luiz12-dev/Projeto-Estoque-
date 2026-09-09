package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.response.ResumoEmpresaPecasResponse;

import com.metalurgica.estoque.dto.request.PecaCortadaRequest;
import com.metalurgica.estoque.dto.response.PecaCortadaResponse;
import com.metalurgica.estoque.service.PecaCortadaService;
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
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PecaCortadaController {

    private final PecaCortadaService pecaCortadaService;

    @PostMapping("/api/pecas-cortadas")
    public ResponseEntity<PecaCortadaResponse> criar(@RequestBody @Valid PecaCortadaRequest request) {
        PecaCortadaResponse response = pecaCortadaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Histórico consolidado de cortes, com filtro por empresa e período. */
    @GetMapping("/api/pecas-cortadas")
    public ResponseEntity<Page<PecaCortadaResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(size = 20, sort = "criadoEm") Pageable pageable) {

        return ResponseEntity.ok(pecaCortadaService.listar(
                busca,
                empresaId,
                dataInicio != null ? dataInicio.atStartOfDay() : null,
                dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null,
                pageable));
    }

    @GetMapping("/api/ordens-servico/{id}/pecas-cortadas")
    public ResponseEntity<List<PecaCortadaResponse>> listarPorOrdemServico(@PathVariable Long id) {
        return ResponseEntity.ok(pecaCortadaService.listarPorOrdemServico(id));
    }

    /** Blocos da tela: uma linha por empresa. */
    // Esta classe nao tem @RequestMapping: cada metodo escreve o caminho inteiro.
    @GetMapping("/api/pecas-cortadas/empresas/resumo")
    public ResponseEntity<List<ResumoEmpresaPecasResponse>> resumoPorEmpresa() {
        return ResponseEntity.ok(pecaCortadaService.resumoPorEmpresa());
    }
}
