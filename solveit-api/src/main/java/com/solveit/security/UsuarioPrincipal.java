package com.solveit.security;

import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UsuarioPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private String password;
    private final PerfilUsuario perfil;
    private final boolean active;

    public UsuarioPrincipal(Usuario usuario) {
        this(usuario, usuario.getSenha());
    }

    private UsuarioPrincipal(Usuario usuario, String password) {
        this.id = usuario.getId();
        this.email = usuario.getEmail();
        this.password = password;
        this.perfil = usuario.getPerfil();
        this.active = Boolean.TRUE.equals(usuario.getAtivo());
    }

    public static UsuarioPrincipal withoutCredentials(Usuario usuario) {
        return new UsuarioPrincipal(usuario, null);
    }

    public Long getId() {
        return id;
    }

    public PerfilUsuario getPerfil() {
        return perfil;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
