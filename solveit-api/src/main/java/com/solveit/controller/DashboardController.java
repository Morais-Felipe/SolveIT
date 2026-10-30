package com.solveit.controller;

import com.solveit.dto.dashboard.DashboardDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    public DashboardDtos.Resumo resumo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return dashboardService.resumo(principal.getId());
    }
}
