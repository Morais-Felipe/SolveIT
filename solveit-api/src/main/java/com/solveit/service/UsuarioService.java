package com.solveit.service;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
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
public class UsuarioService {

    private static final Sort ORDEM_ADMINISTRATIVA = Sort.by(Sort.Order.asc("id"));

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
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

    private static UsuarioDtos.Resposta toResponse(Usuario usuario) {
        return new UsuarioDtos.Resposta(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getAtivo(),
                usuario.getCriadoEm());
    }
}
