package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import com.solveit.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuthLoginIntegrationTest {

    private static final String RAW_PASSWORD = "senha exata com espaços 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_login_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
    }

    @Test
    void authenticatesActiveUserWithNormalizedEmailAndErasesCredentials() {
        Usuario saved = saveUser("felipe@example.com", RAW_PASSWORD, true);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "  FELIPE@Example.COM  ",
                        RAW_PASSWORD));

        assertThat(authenticationManager).isInstanceOf(ProviderManager.class);
        ProviderManager providerManager = (ProviderManager) authenticationManager;
        assertThat(providerManager.getProviders())
                .singleElement()
                .isInstanceOf(DaoAuthenticationProvider.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UsuarioPrincipal.class);

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(saved.getId());
        assertThat(principal.getUsername()).isEqualTo("felipe@example.com");
        assertThat(principal.getPerfil()).isEqualTo(PerfilUsuario.SOLICITANTE);
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SOLICITANTE");
    }

    @Test
    void rejectsIncorrectPasswordThroughDaoAuthenticationProvider() {
        saveUser("felipe@example.com", RAW_PASSWORD, true);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "felipe@example.com",
                        "senha incorreta 123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsInactiveAccountThroughDaoAuthenticationProvider() {
        saveUser("felipe@example.com", RAW_PASSWORD, false);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "felipe@example.com",
                        RAW_PASSWORD)))
                .isInstanceOf(DisabledException.class);
    }

    private Usuario saveUser(String email, String rawPassword, boolean active) {
        Usuario usuario = new Usuario(
                "Felipe",
                email,
                passwordEncoder.encode(rawPassword),
                PerfilUsuario.SOLICITANTE);
        usuario.setAtivo(active);
        return usuarioRepository.saveAndFlush(usuario);
    }
}
