package com.metalurgica.estoque.controller;

import com.metalurgica.estoque.dto.request.PecaCortadaRequest;
import com.metalurgica.estoque.dto.response.PecaCortadaResponse;
import com.metalurgica.estoque.service.PecaCortadaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/api/ordens-servico/{id}/pecas-cortadas")
    public ResponseEntity<List<PecaCortadaResponse>> listarPorOrdemServico(@PathVariable Long id) {
        return ResponseEntity.ok(pecaCortadaService.listarPorOrdemServico(id));
    }
}
