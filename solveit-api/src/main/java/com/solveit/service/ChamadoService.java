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
import com.solveit.repository.ChamadoSpecifications;
import com.solveit.repository.HistoricoStatusRepository;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChamadoService {

    private static final Set<String> CAMPOS_ORDENAVEIS = Set.of("criadoEm", "atualizadoEm", "id");

    private final ChamadoRepository chamadoRepository;
    private final HistoricoStatusRepository historicoStatusRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChamadoAuthorizationService chamadoAuthorizationService;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public ChamadoService(
            ChamadoRepository chamadoRepository,
            HistoricoStatusRepository historicoStatusRepository,
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository,
            ChamadoAuthorizationService chamadoAuthorizationService,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.chamadoRepository = chamadoRepository;
        this.historicoStatusRepository = historicoStatusRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.chamadoAuthorizationService = chamadoAuthorizationService;
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

    @Transactional(readOnly = true)
    public ChamadoDtos.Pagina listar(
            Long usuarioId,
            ChamadoDtos.Filtros filtros,
            int page,
            int size,
            String sort) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));

        Specification<Chamado> consulta = Specification
                .where(ChamadoSpecifications.visivelPara(usuario))
                .and(ChamadoSpecifications.comStatus(filtros.status()))
                .and(ChamadoSpecifications.comPrioridade(filtros.prioridade()))
                .and(ChamadoSpecifications.daCategoria(filtros.categoriaId()))
                .and(ChamadoSpecifications.doAtendente(filtros.atendenteId()))
                .and(ChamadoSpecifications.doSolicitante(filtros.solicitanteId()));
        Page<Chamado> chamados = chamadoRepository.findAll(
                consulta,
                PageRequest.of(page, size, ordenacao(sort)));

        return new ChamadoDtos.Pagina(
                chamados.getContent().stream().map(ChamadoService::toDetalhe).toList(),
                chamados.getNumber(),
                chamados.getSize(),
                chamados.getTotalElements(),
                chamados.getTotalPages());
    }

    /**
     * Aceita somente "campo,direcao" com campos de uma lista fechada. O id entra sempre
     * como desempate na mesma direção, para que chamados com a mesma data não troquem
     * de página entre consultas.
     */
    private static Sort ordenacao(String sort) {
        String[] partes = sort.split(",", -1);
        if (partes.length != 2 || !CAMPOS_ORDENAVEIS.contains(partes[0])) {
            throw parametroInvalido();
        }
        Sort.Direction direcao = switch (partes[1].toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw parametroInvalido();
        };
        Sort principal = Sort.by(direcao, partes[0]);
        return "id".equals(partes[0]) ? principal : principal.and(Sort.by(direcao, "id"));
    }

    private static DomainException parametroInvalido() {
        return new DomainException(
                ApiErrorCode.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Ordenação aceita criadoEm, atualizadoEm ou id, com asc ou desc.");
    }

    @Transactional(readOnly = true)
    public ChamadoDtos.Detalhe buscarPorId(Long usuarioId, Long chamadoId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        chamadoAuthorizationService.exigirAcesso(usuario, chamado);
        return toDetalhe(chamado);
    }

    /**
     * Ordem do protocolo: guard → ator atual → chamado travado → papel/relação → versão →
     * estado. Só título e descrição mudam; a edição não é transição de status e por isso
     * não gera HistoricoStatus.
     */
    @Transactional(timeout = 10)
    public ChamadoDtos.Detalhe editar(
            Long usuarioId,
            Long chamadoId,
            ChamadoDtos.Editar request) {
        coreWriteCoordinator.adquirir();
        Usuario usuario = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.refresh(chamado, LockModeType.PESSIMISTIC_WRITE);

        boolean donoSolicitante = usuario.getPerfil() == PerfilUsuario.SOLICITANTE
                && Objects.equals(chamado.getSolicitante().getId(), usuario.getId());
        if (usuario.getPerfil() != PerfilUsuario.ADMIN && !donoSolicitante) {
            throw new AccessDeniedException("Only the requester or an administrator edits a ticket");
        }
        if (!Objects.equals(chamado.getVersion(), request.versaoEsperada())) {
            throw new DomainException(
                    ApiErrorCode.STALE_RESOURCE,
                    HttpStatus.CONFLICT,
                    "Recurso desatualizado",
                    "O recurso foi alterado por outra operação. Atualize os dados e tente novamente.");
        }
        if (chamado.getStatus() != StatusChamado.ABERTO) {
            throw new DomainException(
                    ApiErrorCode.INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Chamado não editável",
                    "Somente chamados abertos podem ter título e descrição alterados.");
        }

        chamado.setTitulo(request.titulo());
        chamado.setDescricao(request.descricao());
        return toDetalhe(chamadoRepository.saveAndFlush(chamado));
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
