package com.solveit.service;

import com.solveit.dto.historico.HistoricoStatusDtos;
import com.solveit.entity.Chamado;
import com.solveit.entity.HistoricoStatus;
import com.solveit.entity.Usuario;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.HistoricoStatusRepository;
import com.solveit.repository.UsuarioRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoStatusService {

    private final HistoricoStatusRepository historicoStatusRepository;
    private final ChamadoRepository chamadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChamadoAuthorizationService chamadoAuthorizationService;

    public HistoricoStatusService(
            HistoricoStatusRepository historicoStatusRepository,
            ChamadoRepository chamadoRepository,
            UsuarioRepository usuarioRepository,
            ChamadoAuthorizationService chamadoAuthorizationService) {
        this.historicoStatusRepository = historicoStatusRepository;
        this.chamadoRepository = chamadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.chamadoAuthorizationService = chamadoAuthorizationService;
    }

    /**
     * Devolve exatamente os registros gravados, sem paginação e sem reconstruir nada a
     * partir do estado atual do chamado. O acesso é o mesmo do detalhe do chamado.
     */
    @Transactional(readOnly = true)
    public List<HistoricoStatusDtos.Resposta> listar(Long usuarioId, Long chamadoId) {
        Usuario leitor = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        chamadoAuthorizationService.exigirAcesso(leitor, chamado);

        return historicoStatusRepository.findByChamadoIdOrderByAlteradoEmAscIdAsc(chamadoId)
                .stream()
                .map(HistoricoStatusService::toResposta)
                .toList();
    }

    private static HistoricoStatusDtos.Resposta toResposta(HistoricoStatus historico) {
        Usuario autor = historico.getAlteradoPor();
        return new HistoricoStatusDtos.Resposta(
                historico.getId(),
                historico.getStatusAnterior(),
                historico.getStatusNovo(),
                new HistoricoStatusDtos.Autor(autor.getId(), autor.getNome()),
                historico.getAlteradoEm(),
                historico.getObservacao());
    }
}
