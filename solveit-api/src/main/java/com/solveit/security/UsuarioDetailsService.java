package com.solveit.security;

import com.solveit.entity.Usuario;
import com.solveit.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new UsuarioPrincipal(usuario);
    }
}
