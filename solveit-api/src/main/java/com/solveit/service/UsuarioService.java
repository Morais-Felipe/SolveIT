package com.solveit.service;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.exception.EmailAlreadyRegisteredException;
import com.solveit.exception.UniqueConstraints;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final PasswordEncoder passwordEncoder;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
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
