package com.solveit.service;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.UsuarioRepository;
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

    public CategoriaService(
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
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
                categoria.getAtivo());
    }
}
