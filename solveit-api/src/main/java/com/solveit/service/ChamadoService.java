package com.solveit.service;

import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.HistoricoStatus;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.HistoricoStatusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChamadoService {

    private final ChamadoRepository chamadoRepository;
    private final HistoricoStatusRepository historicoStatusRepository;
    private final CategoriaRepository categoriaRepository;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public ChamadoService(
            ChamadoRepository chamadoRepository,
            HistoricoStatusRepository historicoStatusRepository,
            CategoriaRepository categoriaRepository,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.chamadoRepository = chamadoRepository;
        this.historicoStatusRepository = historicoStatusRepository;
        this.categoriaRepository = categoriaRepository;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
    }

    /**
     * Chamado e histórico de abertura são gravados na mesma transação: se o histórico
     * falhar, o chamado também é desfeito e nenhum dos dois existe.
     */
    @Transactional(timeout = 10)
    public ChamadoDtos.Detalhe abrir(Long usuarioId, ChamadoDtos.Criar request) {
        coreWriteCoordinator.adquirir();
        Usuario solicitante = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        if (solicitante.getPerfil() != PerfilUsuario.SOLICITANTE) {
            throw new AccessDeniedException("Opening tickets requires requester profile");
        }

        Categoria categoria = carregarCategoriaAtivaBloqueada(request.categoriaId());

        Chamado chamado = chamadoRepository.saveAndFlush(new Chamado(
                request.titulo(),
                request.descricao(),
                StatusChamado.ABERTO,
                request.prioridade(),
                solicitante,
                null,
                categoria));
        historicoStatusRepository.saveAndFlush(HistoricoStatus.abertura(chamado, solicitante));

        return toDetalhe(chamado);
    }

    private Categoria carregarCategoriaAtivaBloqueada(Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.refresh(categoria, LockModeType.PESSIMISTIC_WRITE);
        if (!Boolean.TRUE.equals(categoria.getAtivo())) {
            throw new DomainException(
                    ApiErrorCode.INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Categoria inativa",
                    "A categoria selecionada está inativa. Escolha outra categoria.");
        }
        return categoria;
    }

    private static ChamadoDtos.Detalhe toDetalhe(Chamado chamado) {
        Usuario atendente = chamado.getAtendente();
        Categoria categoria = chamado.getCategoria();
        return new ChamadoDtos.Detalhe(
                chamado.getId(),
                chamado.getTitulo(),
                chamado.getDescricao(),
                chamado.getStatus(),
                chamado.getPrioridade(),
                new ChamadoDtos.Pessoa(
                        chamado.getSolicitante().getId(),
                        chamado.getSolicitante().getNome()),
                atendente == null
                        ? null
                        : new ChamadoDtos.Pessoa(atendente.getId(), atendente.getNome()),
                new ChamadoDtos.CategoriaResumo(
                        categoria.getId(),
                        categoria.getNome(),
                        categoria.getAtivo()),
                chamado.getCriadoEm(),
                chamado.getAtualizadoEm(),
                chamado.getResolvidoEm(),
                chamado.getVersion());
    }
}
