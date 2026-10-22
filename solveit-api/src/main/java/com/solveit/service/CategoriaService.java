package com.solveit.service;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.exception.UniqueConstraints;
import com.solveit.repository.CategoriaRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoriaService {

    private static final Sort ORDEM_CATALOGO = Sort.by(
            Sort.Order.asc("nome"),
            Sort.Order.asc("id"));

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public CategoriaService(
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public CategoriaDtos.Pagina listarAtivas(Long usuarioId, int page, int size) {
        carregarUsuarioAtivo(usuarioId);
        Page<Categoria> categorias = categoriaRepository.findByAtivoTrue(
                PageRequest.of(page, size, ORDEM_CATALOGO));

        return new CategoriaDtos.Pagina(
                categorias.getContent().stream().map(CategoriaService::toResponse).toList(),
                categorias.getNumber(),
                categorias.getSize(),
                categorias.getTotalElements(),
                categorias.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CategoriaDtos.Resposta buscarPorId(Long usuarioId, Long categoriaId) {
        Usuario usuario = carregarUsuarioAtivo(usuarioId);
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(categoria.getAtivo())
                && usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException("Inactive category requires administrator profile");
        }

        return toResponse(categoria);
    }

    @Transactional(timeout = 10)
    public CategoriaDtos.Resposta criar(Long usuarioId, CategoriaDtos.Criar request) {
        coreWriteCoordinator.adquirir();
        Usuario usuario = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException("Category creation requires administrator profile");
        }

        String nome = request.nome().trim();
        if (categoriaRepository.existsByNome(nome)) {
            throw categoryNameAlreadyExists();
        }

        Categoria categoria = new Categoria(nome, request.descricao());
        try {
            return toResponse(categoriaRepository.saveAndFlush(categoria));
        } catch (DataIntegrityViolationException exception) {
            if (UniqueConstraints.isViolationOf(exception, UniqueConstraints.CATEGORIA_NOME)) {
                throw categoryNameAlreadyExists();
            }
            throw exception;
        }
    }

    @Transactional(timeout = 10)
    public CategoriaDtos.Resposta editar(
            Long usuarioId,
            Long categoriaId,
            CategoriaDtos.Editar request) {
        coreWriteCoordinator.adquirir();
        validarAdminAtual(usuarioId, "Category editing requires administrator profile");
        Categoria categoria = carregarCategoriaBloqueada(categoriaId);
        validarVersao(categoria, request.versaoEsperada());

        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        try {
            return toResponse(categoriaRepository.saveAndFlush(categoria));
        } catch (DataIntegrityViolationException exception) {
            if (UniqueConstraints.isViolationOf(exception, UniqueConstraints.CATEGORIA_NOME)) {
                throw categoryNameAlreadyExists();
            }
            throw exception;
        }
    }

    @Transactional(timeout = 10)
    public CategoriaDtos.Resposta alterarAtivo(
            Long usuarioId,
            Long categoriaId,
            CategoriaDtos.AlterarAtivo request) {
        coreWriteCoordinator.adquirir();
        validarAdminAtual(usuarioId, "Category activation requires administrator profile");
        Categoria categoria = carregarCategoriaBloqueada(categoriaId);
        validarVersao(categoria, request.versaoEsperada());

        if (Objects.equals(categoria.getAtivo(), request.ativo())) {
            return toResponse(categoria);
        }

        categoria.setAtivo(request.ativo());
        return toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    private void validarAdminAtual(Long usuarioId, String accessDeniedMessage) {
        Usuario usuario = coreWriteCoordinator.carregarUsuarioAtual(usuarioId);
        if (usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException(accessDeniedMessage);
        }
    }

    private Categoria carregarCategoriaBloqueada(Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.refresh(categoria, LockModeType.PESSIMISTIC_WRITE);
        return categoria;
    }

    private static void validarVersao(Categoria categoria, Long versaoEsperada) {
        if (!Objects.equals(categoria.getVersion(), versaoEsperada)) {
            throw staleResource();
        }
    }

    private Usuario carregarUsuarioAtivo(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new AccessDeniedException("Authenticated user is inactive");
        }
        return usuario;
    }

    private static CategoriaDtos.Resposta toResponse(Categoria categoria) {
        return new CategoriaDtos.Resposta(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getAtivo(),
                categoria.getVersion());
    }

    private static DomainException categoryNameAlreadyExists() {
        return new DomainException(
                ApiErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "Conflito de categoria",
                "Já existe uma categoria com este nome.");
    }

    private static DomainException staleResource() {
        return new DomainException(
                ApiErrorCode.STALE_RESOURCE,
                HttpStatus.CONFLICT,
                "Recurso desatualizado",
                "O recurso foi alterado por outra operação. Atualize os dados e tente novamente.");
    }
}
