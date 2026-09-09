package com.solveit.security;

import com.solveit.entity.Usuario;
import com.solveit.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization != null) {
            if (authorization.startsWith(BEARER_PREFIX)
                    && authorization.length() > BEARER_PREFIX.length()) {
                authenticateBearer(
                        request,
                        authorization.substring(BEARER_PREFIX.length()));
            } else {
                reject(request, AuthenticationFailureReason.TOKEN_INVALID);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateBearer(HttpServletRequest request, String token) {
        JwtService.AuthenticationResult result = jwtService.parseForAuthentication(token);
        if (!result.isAccepted()) {
            reject(request, result.failureReason());
            return;
        }

        Long usuarioId;
        try {
            usuarioId = Long.valueOf(result.claims().getSubject());
        } catch (IllegalArgumentException ignored) {
            reject(request, AuthenticationFailureReason.TOKEN_INVALID);
            return;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            reject(request, AuthenticationFailureReason.TOKEN_INVALID);
            return;
        }
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            reject(request, AuthenticationFailureReason.ACCOUNT_INACTIVE);
            return;
        }

        UsuarioPrincipal principal = UsuarioPrincipal.withoutCredentials(usuario);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private void reject(
            HttpServletRequest request,
            AuthenticationFailureReason failureReason) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AuthenticationFailureReason.REQUEST_ATTRIBUTE, failureReason);
    }
}
