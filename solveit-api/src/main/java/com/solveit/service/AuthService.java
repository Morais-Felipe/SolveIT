package com.solveit.service;

import com.solveit.config.JwtProperties;
import com.solveit.dto.auth.LoginRequest;
import com.solveit.dto.auth.RegisterRequest;
import com.solveit.dto.auth.TokenResponse;
import com.solveit.dto.auth.UsuarioResponse;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.EmailAlreadyRegisteredException;
import com.solveit.exception.InvalidCredentialsException;
import com.solveit.repository.UsuarioRepository;
import com.solveit.security.JwtService;
import com.solveit.security.UsuarioPrincipal;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        String passwordHash = passwordEncoder.encode(request.senha());

        Usuario usuario = new Usuario(
                request.nome(),
                normalizedEmail,
                passwordHash,
                PerfilUsuario.SOLICITANTE);
        Usuario saved;
        try {
            saved = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            normalizedEmail,
                            request.senha()));
        } catch (AuthenticationException ignored) {
            throw new InvalidCredentialsException();
        }
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String accessToken = jwtService.issue(principal.getId());

        return new TokenResponse(
                accessToken,
                "Bearer",
                jwtProperties.getAccessTokenTtl().toSeconds());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse me(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(IllegalStateException::new);
        return toResponse(usuario);
    }

    private static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getAtivo(),
                usuario.getCriadoEm());
    }

    private static boolean isEmailUniqueConstraintViolation(
            DataIntegrityViolationException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && isEmailConstraint(constraintViolation.getConstraintName())) {
                return true;
            }

            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }

        return false;
    }

    private static boolean isEmailConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }

        String unquotedName = constraintName.replace("`", "").replace("\"", "");
        int lastQualifier = unquotedName.lastIndexOf('.');
        String simpleName = lastQualifier >= 0
                ? unquotedName.substring(lastQualifier + 1)
                : unquotedName;

        return "uk_usuario_email".equalsIgnoreCase(simpleName);
    }
}
