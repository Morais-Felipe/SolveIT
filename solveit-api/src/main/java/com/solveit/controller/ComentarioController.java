package com.solveit.controller;

import com.solveit.dto.comentario.ComentarioDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.ComentarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chamados/{chamadoId}/comentarios")
public class ComentarioController {

    private final ComentarioService comentarioService;

    public ComentarioController(ComentarioService comentarioService) {
        this.comentarioService = comentarioService;
    }

    /** 201 sem Location: não existe rota para um comentário individual. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ComentarioDtos.Resposta adicionar(
            @PathVariable Long chamadoId,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody ComentarioDtos.Criar request) {
        return comentarioService.adicionar(principal.getId(), chamadoId, request);
    }
}
