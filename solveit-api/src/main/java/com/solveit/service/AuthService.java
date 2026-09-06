package com.solveit.service;

import com.solveit.dto.auth.RegisterRequest;
import com.solveit.dto.auth.UsuarioResponse;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.EmailAlreadyRegisteredException;
import com.solveit.repository.UsuarioRepository;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

        return new UsuarioResponse(
                saved.getId(),
                saved.getNome(),
                saved.getEmail(),
                saved.getPerfil(),
                saved.getAtivo(),
                saved.getCriadoEm());
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
