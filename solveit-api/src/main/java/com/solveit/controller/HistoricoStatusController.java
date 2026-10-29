package com.solveit.controller;

import com.solveit.dto.historico.HistoricoStatusDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.HistoricoStatusService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chamados/{chamadoId}/historico")
public class HistoricoStatusController {

    private final HistoricoStatusService historicoStatusService;

    public HistoricoStatusController(HistoricoStatusService historicoStatusService) {
        this.historicoStatusService = historicoStatusService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<HistoricoStatusDtos.Resposta> listar(
            @PathVariable Long chamadoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return historicoStatusService.listar(principal.getId(), chamadoId);
    }
}
