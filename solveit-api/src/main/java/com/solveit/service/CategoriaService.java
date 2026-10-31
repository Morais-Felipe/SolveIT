package com.solveit.service;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.exception.UniqueConstraints;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.ChamadoSpecifications;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
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
public class CategoriaService {

    private static final Sort ORDEM_CATALOGO = Sort.by(
            Sort.Order.asc("nome"),
            Sort.Order.asc("id"));

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChamadoRepository chamadoRepository;
    private final CoreWriteCoordinator coreWriteCoordinator;
    private final EntityManager entityManager;

    public CategoriaService(
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository,
            ChamadoRepository chamadoRepository,
            CoreWriteCoordinator coreWriteCoordinator,
            EntityManager entityManager) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.chamadoRepository = chamadoRepository;
        this.coreWriteCoordinator = coreWriteCoordinator;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public CategoriaDtos.Pagina listar(Long usuarioId, boolean incluirInativas, int page, int size) {
        Usuario usuario = carregarUsuarioAtivo(usuarioId);
        // Plano 06-05 D-1: o catálogo completo (com inativas) existe só para administrar.
        if (incluirInativas && usuario.getPerfil() != PerfilUsuario.ADMIN) {
            throw new AccessDeniedException("Listing inactive categories requires administrator profile");
        }
        PageRequest pagina = PageRequest.of(page, size, ORDEM_CATALOGO);
        Page<Categoria> categorias = incluirInativas
                ? categoriaRepository.findAll(pagina)
                : categoriaRepository.findByAtivoTrue(pagina);

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

        // Decisão linked-visible-ticket (03-05): categoria inativa é legível pelo ADMIN ou
        // por quem hoje pode ver ao menos um chamado vinculado a ela. Isso autoriza só
        // esta leitura; não amplia o acesso a chamado algum.
        if (!Boolean.TRUE.equals(categoria.getAtivo())
                && usuario.getPerfil() != PerfilUsuario.ADMIN
                && !chamadoRepository.exists(Specification
                        .where(ChamadoSpecifications.visivelPara(usuario))
                        .and(ChamadoSpecifications.daCategoria(categoria.getId())))) {
            throw new AccessDeniedException(
                    "Inactive category requires administrator or a visible linked ticket");
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
