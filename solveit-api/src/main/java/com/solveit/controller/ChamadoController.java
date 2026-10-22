package com.solveit.controller;

import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.ChamadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
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

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ChamadoDtos.Pagina listar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) StatusChamado status,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false)
            @Positive(message = "A categoria deve ser um identificador positivo")
            Long categoriaId,
            @RequestParam(required = false)
            @Positive(message = "O atendente deve ser um identificador positivo")
            Long atendenteId,
            @RequestParam(required = false)
            @Positive(message = "O solicitante deve ser um identificador positivo")
            Long solicitanteId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "A página não pode ser negativa")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "O tamanho da página deve ser entre 1 e 100")
            @Max(value = 100, message = "O tamanho da página deve ser entre 1 e 100")
            int size,
            @RequestParam(defaultValue = "criadoEm,desc") String sort) {
        return chamadoService.listar(
                principal.getId(),
                new ChamadoDtos.Filtros(status, prioridade, categoriaId, atendenteId, solicitanteId),
                page,
                size,
                sort);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SOLICITANTE', 'ADMIN')")
    public ChamadoDtos.Detalhe editar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody ChamadoDtos.Editar request) {
        return chamadoService.editar(principal.getId(), id, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ChamadoDtos.Detalhe buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return chamadoService.buscarPorId(principal.getId(), id);
    }
}
