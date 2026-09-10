package com.solveit.security;

import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.ProblemDetailFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public final class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problemDetailFactory;

    public RestAccessDeniedHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        problemDetailFactory.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCESS_DENIED,
                "Acesso negado",
                "Você não tem permissão para realizar esta operação.");
    }
}
