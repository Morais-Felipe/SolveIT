package com.solveit.service;

import com.solveit.dto.dashboard.DashboardDtos;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.DashboardRepository;
import com.solveit.repository.UsuarioRepository;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final UsuarioRepository usuarioRepository;

    public DashboardService(
            DashboardRepository dashboardRepository,
            UsuarioRepository usuarioRepository) {
        this.dashboardRepository = dashboardRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Indicadores no escopo de leitura do perfil atual (D-1): ADMIN vê tudo; ATENDENTE vê
     * a fila e os chamados atribuídos a ele. O perfil é relido do banco.
     */
    @Transactional(readOnly = true)
    public DashboardDtos.Resumo resumo(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        if (usuario.getPerfil() == PerfilUsuario.SOLICITANTE
                || !Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new AccessDeniedException("Dashboard requires administrator or attendant profile");
        }

        Map<StatusChamado, Long> porStatus = dashboardRepository.contarPorStatus(usuario);
        Map<Prioridade, Long> abertosPorPrioridade =
                dashboardRepository.abertosPorPrioridade(usuario);

        return new DashboardDtos.Resumo(
                new DashboardDtos.Totais(
                        porStatus.get(StatusChamado.ABERTO),
                        porStatus.get(StatusChamado.EM_ANDAMENTO),
                        porStatus.get(StatusChamado.RESOLVIDO),
                        abertosPorPrioridade.get(Prioridade.CRITICA)),
                dashboardRepository.porCategoria(usuario),
                dashboardRepository.cargaPorAtendente(usuario),
                abertosPorPrioridade);
    }
}
