package com.solveit.security;

import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import java.util.Collection;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UsuarioPrincipal implements UserDetails, CredentialsContainer {

    public UsuarioPrincipal(Usuario usuario) {
    }

    public Long getId() {
        throw new UnsupportedOperationException("Principal ID is not implemented yet");
    }

    public PerfilUsuario getPerfil() {
        throw new UnsupportedOperationException("Principal profile is not implemented yet");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        throw new UnsupportedOperationException("Authorities are not implemented yet");
    }

    @Override
    public String getPassword() {
        throw new UnsupportedOperationException("Password access is not implemented yet");
    }

    @Override
    public String getUsername() {
        throw new UnsupportedOperationException("Username access is not implemented yet");
    }

    @Override
    public boolean isEnabled() {
        throw new UnsupportedOperationException("Account status is not implemented yet");
    }

    @Override
    public void eraseCredentials() {
        throw new UnsupportedOperationException("Credential erasure is not implemented yet");
    }
}
