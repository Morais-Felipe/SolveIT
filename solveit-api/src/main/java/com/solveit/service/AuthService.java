package com.solveit.service;

import com.solveit.dto.auth.RegisterRequest;
import com.solveit.dto.auth.UsuarioResponse;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.senha());

        Usuario usuario = new Usuario(
                request.nome(),
                normalizedEmail,
                passwordHash,
                PerfilUsuario.SOLICITANTE);
        Usuario saved = usuarioRepository.save(usuario);

        return new UsuarioResponse(
                saved.getId(),
                saved.getNome(),
                saved.getEmail(),
                saved.getPerfil(),
                saved.getAtivo(),
                saved.getCriadoEm());
    }
}
