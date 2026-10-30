package com.solveit.service;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.entity.Chamado;
import com.solveit.entity.HistoricoStatus;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.exception.EmailAlreadyRegisteredException;
import com.solveit.exception.UniqueConstraints;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.HistoricoStatusRepository;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {

    private static final Sort ORDEM_ADMINISTRATIVA = Sort.by(Sort.Order.asc("id"));

    private final UsuarioRepository usuarioRepository;
    private final ChamadoRepository chamadoRepository;
    private final HistoricoStatusRepository historicoStatusRepository;
    private final PasswordEncoder passwordEncoder;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;
    private final Clock clock;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            ChamadoRepository chamadoRepository,
            HistoricoStatusRepository historicoStatusRepository,
            PasswordEncoder passwordEncoder,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager,
            Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.chamadoRepository = chamadoRepository;
        this.historicoStatusRepository = historicoStatusRepository;
        this.passwordEncoder = passwordEncoder;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsuarioDtos.Pagina listar(Long usuarioId, int page, int size) {
        validarAdminAtivo(usuarioId);
        Page<Usuario> usuarios = usuarioRepository.findAll(
                PageRequest.of(page, size, ORDEM_ADMINISTRATIVA));

        return new UsuarioDtos.Pagina(
                usuarios.getContent().stream().map(UsuarioService::toResponse).toList(),
                usuarios.getNumber(),
                usuarios.getSize(),
                usuarios.getTotalElements(),
                usuarios.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UsuarioDtos.Resposta buscarPorId(Long usuarioId, Long alvoId) {
        validarAdminAtivo(usuarioId);
        Usuario alvo = usuarioRepository.findById(alvoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponse(alvo);
    }

    @Transactional(timeout = 10)
    public UsuarioDtos.Resposta criar(Long usuarioId, UsuarioDtos.Criar request) {
        // BCrypt é deliberadamente lento e não lê estado do banco: calcular antes
        // do guard evita segurar a fila de escritas durante o hash.
        String senhaHash = passwordEncoder.encode(request.senha());

        coreWriteCoordinator.adquirir();
        validarAdminAtual(usuarioId, "User creation requires administrator profile");

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        Usuario usuario = new Usuario(
                request.nome(),
                request.email(),
                senhaHash,
                request.perfil());
        return toResponse(salvarComEmailUnico(usuario));
    }

    @Transactional(timeout = 10)
    public UsuarioDtos.Resposta editarDados(
            Long usuarioId,
            Long alvoId,
            UsuarioDtos.EditarDados request) {
        String novaSenhaHash = request.senha() == null
                ? null
                : passwordEncoder.encode(request.senha());

        coreWriteCoordinator.adquirir();
        Usuario alvo = bloquearAtorEAlvoEmOrdemDeId(
                usuarioId,
                alvoId,
                "User editing requires administrator profile");
        validarVersao(alvo, request.versaoEsperada());

        if (!request.email().equals(alvo.getEmail())
                && usuarioRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        alvo.setNome(request.nome());
        alvo.setEmail(request.email());
        if (novaSenhaHash != null) {
            alvo.setSenha(novaSenhaHash);
        }
        return toResponse(salvarComEmailUnico(alvo));
    }

    /**
     * Altera perfil e atividade numa única transação (revisão de escopo de 2026-10-30:
     * sem prévia). Ordem: guard → ator e alvo por id → versão → mudança real → último
     * ADMIN → devolução dos chamados EM_ANDAMENTO do atendente → usuário. Qualquer falha
     * desfaz o usuário e todos os chamados.
     */
    @Transactional(timeout = 10)
    public UsuarioDtos.ResultadoOperacao alterarOperacao(
            Long usuarioId,
            Long alvoId,
            UsuarioDtos.AlterarOperacao request) {
        coreWriteCoordinator.adquirir();
        Usuario alvo = bloquearAtorEAlvoEmOrdemDeId(
                usuarioId,
                alvoId,
                "User operation changes require administrator profile");
        Usuario ator = entityManager.find(Usuario.class, usuarioId);
        validarVersao(alvo, request.versaoEsperada());

        PerfilUsuario novoPerfil = request.perfil();
        boolean novoAtivo = request.ativo();
        boolean ativoAtual = Boolean.TRUE.equals(alvo.getAtivo());
        if (alvo.getPerfil() == novoPerfil && ativoAtual == novoAtivo) {
            throw new DomainException(
                    ApiErrorCode.INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Nenhuma alteração",
                    "O usuário já possui o perfil e o estado solicitados.");
        }

        boolean deixaDeSerAdminAtivo = alvo.getPerfil() == PerfilUsuario.ADMIN
                && ativoAtual
                && !(novoPerfil == PerfilUsuario.ADMIN && novoAtivo);
        if (deixaDeSerAdminAtivo
                && usuarioRepository.countByPerfilAndAtivoTrue(PerfilUsuario.ADMIN) <= 1) {
            throw new DomainException(
                    ApiErrorCode.LAST_ACTIVE_ADMIN,
                    HttpStatus.CONFLICT,
                    "Último administrador",
                    "O sistema precisa manter ao menos um administrador ativo.");
        }

        int devolvidos = 0;
        boolean deixaDeAtender = alvo.getPerfil() == PerfilUsuario.ATENDENTE
                && !(novoPerfil == PerfilUsuario.ATENDENTE && novoAtivo);
        if (deixaDeAtender) {
            String motivo = novoAtivo
                    ? "Retorno à fila por mudança de perfil do atendente"
                    : "Retorno à fila por desativação do atendente";
            devolvidos = devolverChamadosAFila(alvo, ator, motivo);
        }

        alvo.setPerfil(novoPerfil);
        alvo.setAtivo(novoAtivo);
        Usuario salvo = usuarioRepository.saveAndFlush(alvo);
        return new UsuarioDtos.ResultadoOperacao(toResponse(salvo), devolvidos);
    }

    /**
     * Devolve cada chamado EM_ANDAMENTO do atendente à fila: ABERTO, sem responsável, com
     * um histórico de autoria do ADMIN e motivo automático. Encerrados não mudam.
     */
    private int devolverChamadosAFila(Usuario atendente, Usuario ator, String motivo) {
        List<Chamado> emAndamento = chamadoRepository.findByAtendenteIdAndStatusOrderByIdAsc(
                atendente.getId(),
                StatusChamado.EM_ANDAMENTO);
        LocalDateTime instante = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        for (Chamado chamado : emAndamento) {
            entityManager.refresh(chamado, LockModeType.PESSIMISTIC_WRITE);
            chamado.setAtendente(null);
            chamado.transicionar(StatusChamado.ABERTO, instante);
            Chamado atualizado = chamadoRepository.saveAndFlush(chamado);
            historicoStatusRepository.saveAndFlush(HistoricoStatus.transicao(
                    atualizado,
                    StatusChamado.EM_ANDAMENTO,
                    ator,
                    motivo));
        }
        return emAndamento.size();
    }

    /**
     * Trava os usuários envolvidos sempre em ordem crescente de ID, para que duas
     * transações nunca esperem uma pela outra em ordens opostas (deadlock). O 404 do
     * alvo só é revelado depois de confirmar que o ator ainda é ADMIN ativo.
     */
    private Usuario bloquearAtorEAlvoEmOrdemDeId(
            Long usuarioId,
            Long alvoId,
            String accessDeniedMessage) {
        if (usuarioId.equals(alvoId)) {
            return validarAdminAtual(usuarioId, accessDeniedMessage);
        }

        Usuario alvo;
        if (alvoId < usuarioId) {
            alvo = entityManager.find(Usuario.class, alvoId, LockModeType.PESSIMISTIC_WRITE);
            validarAdminAtual(usuarioId, accessDeniedMessage);
        } else {
            validarAdminAtual(usuarioId, accessDeniedMessage);
            alvo = entityManager.find(Usuario.class, alvoId, LockModeType.PESSIMISTIC_WRITE);
        }

        if (alvo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return alvo;
    }

    private Usuario validarAdminAtual(Long usuarioId, String accessDeniedMessage) {
        Usuario usuario = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException(accessDeniedMessage);
        }
        return usuario;
    }

    private void validarAdminAtivo(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new AccessDeniedException("Authenticated user is inactive");
        }
        if (usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException("User administration requires administrator profile");
        }
    }

    private Usuario salvarComEmailUnico(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException exception) {
            if (UniqueConstraints.isViolationOf(exception, UniqueConstraints.USUARIO_EMAIL)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }

    private static void validarVersao(Usuario usuario, Long versaoEsperada) {
        if (!Objects.equals(usuario.getVersion(), versaoEsperada)) {
            throw new DomainException(
                    ApiErrorCode.STALE_RESOURCE,
                    HttpStatus.CONFLICT,
                    "Recurso desatualizado",
                    "O recurso foi alterado por outra operação. Atualize os dados e tente novamente.");
        }
    }

    private static UsuarioDtos.Resposta toResponse(Usuario usuario) {
        return new UsuarioDtos.Resposta(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getAtivo(),
                usuario.getCriadoEm(),
                usuario.getVersion());
    }
}
