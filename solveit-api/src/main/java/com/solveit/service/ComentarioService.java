package com.solveit.service;

import com.solveit.dto.comentario.ComentarioDtos;
import com.solveit.entity.Chamado;
import com.solveit.entity.Comentario;
import com.solveit.entity.Usuario;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.ComentarioRepository;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComentarioService {

    private static final Set<StatusChamado> CONVERSA_ENCERRADA =
            Set.of(StatusChamado.FECHADO, StatusChamado.CANCELADO);

    private final ComentarioRepository comentarioRepository;
    private final ChamadoRepository chamadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChamadoAuthorizationService chamadoAuthorizationService;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public ComentarioService(
            ComentarioRepository comentarioRepository,
            ChamadoRepository chamadoRepository,
            UsuarioRepository usuarioRepository,
            ChamadoAuthorizationService chamadoAuthorizationService,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.comentarioRepository = comentarioRepository;
        this.chamadoRepository = chamadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.chamadoAuthorizationService = chamadoAuthorizationService;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
    }

    /**
     * Mesma regra de acesso do detalhe do chamado, avaliada com o perfil e a relação
     * atuais. Encerrados continuam legíveis; ter comentado não concede leitura.
     */
    @Transactional(readOnly = true)
    public List<ComentarioDtos.Resposta> listar(Long usuarioId, Long chamadoId) {
        Usuario leitor = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        chamadoAuthorizationService.exigirAcesso(leitor, chamado);

        return comentarioRepository.findByChamadoIdOrderByCriadoEmAscIdAsc(chamadoId).stream()
                .map(ComentarioService::toResposta)
                .toList();
    }

    /**
     * Ordem: guard → ator → chamado travado → acesso atual (403) → conversa aberta (409).
     * O chamado é travado só para ler um status confirmado; ele não é alterado, então
     * não há versão esperada, nem mudança de datas, nem HistoricoStatus.
     */
    @Transactional(timeout = 10)
    public ComentarioDtos.Resposta adicionar(
            Long usuarioId,
            Long chamadoId,
            ComentarioDtos.Criar request) {
        coreWriteCoordinator.adquirir();
        Usuario autor = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.refresh(chamado, LockModeType.PESSIMISTIC_WRITE);

        chamadoAuthorizationService.exigirAcesso(autor, chamado);
        if (CONVERSA_ENCERRADA.contains(chamado.getStatus())) {
            throw new DomainException(
                    ApiErrorCode.COMMENTS_CLOSED,
                    HttpStatus.CONFLICT,
                    "Conversa encerrada",
                    "Este chamado foi encerrado e não aceita novos comentários.");
        }

        return toResposta(comentarioRepository.saveAndFlush(
                new Comentario(chamado, autor, request.texto())));
    }

    private static ComentarioDtos.Resposta toResposta(Comentario comentario) {
        Usuario autor = comentario.getAutor();
        return new ComentarioDtos.Resposta(
                comentario.getId(),
                comentario.getTexto(),
                new ComentarioDtos.Autor(autor.getId(), autor.getNome()),
                comentario.getCriadoEm());
    }
}
