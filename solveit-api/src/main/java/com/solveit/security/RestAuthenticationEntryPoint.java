package com.solveit.security;

import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.ProblemDetailFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public final class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailFactory problemDetailFactory;

    public RestAuthenticationEntryPoint(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        AuthenticationFailureReason failureReason = failureReason(request);
        ProblemDefinition problem = problemFor(failureReason);

        problemDetailFactory.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                problem.code(),
                problem.title(),
                problem.detail());
    }

    private static AuthenticationFailureReason failureReason(HttpServletRequest request) {
        Object attribute = request.getAttribute(AuthenticationFailureReason.REQUEST_ATTRIBUTE);
        return attribute instanceof AuthenticationFailureReason reason ? reason : null;
    }

    private static ProblemDefinition problemFor(AuthenticationFailureReason failureReason) {
        if (failureReason == null) {
            return new ProblemDefinition(
                    ApiErrorCode.AUTH_TOKEN_MISSING,
                    "Autenticação necessária",
                    "Autenticação necessária.");
        }

        return switch (failureReason) {
            case TOKEN_INVALID -> new ProblemDefinition(
                    ApiErrorCode.AUTH_TOKEN_INVALID,
                    "Autenticação inválida",
                    "Sua autenticação não é válida. Entre novamente.");
            case TOKEN_EXPIRED -> new ProblemDefinition(
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Sessão expirada",
                    "Sua sessão expirou. Entre novamente para continuar.");
            case ACCOUNT_INACTIVE -> new ProblemDefinition(
                    ApiErrorCode.AUTH_ACCOUNT_INACTIVE,
                    "Conta inativa",
                    "Sua conta está inativa. Entre em contato com um administrador.");
        };
    }

    private record ProblemDefinition(
            ApiErrorCode code,
            String title,
            String detail) {
    }
}
