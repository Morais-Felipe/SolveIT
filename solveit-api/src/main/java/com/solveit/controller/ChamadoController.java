package com.solveit.controller;

import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.ChamadoService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chamados")
public class ChamadoController {

    private final ChamadoService chamadoService;

    public ChamadoController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SOLICITANTE')")
    public ResponseEntity<ChamadoDtos.Detalhe> abrir(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody ChamadoDtos.Criar request) {
        ChamadoDtos.Detalhe response = chamadoService.abrir(principal.getId(), request);
        return ResponseEntity
                .created(URI.create("/api/chamados/" + response.id()))
                .body(response);
    }
}
