package com.solveit.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailFactory {

    private final ObjectMapper objectMapper;

    public ProblemDetailFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProblemDetail create(
            HttpStatus status,
            ApiErrorCode code,
            String title,
            String detail,
            URI instance,
            Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(code.type());
        problem.setTitle(title);
        problem.setInstance(instance);
        problem.setProperty("code", code.name());

        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", Map.copyOf(errors));
        }

        return problem;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String title,
            String detail) throws IOException {
        ProblemDetail problem = create(
                status,
                code,
                title,
                detail,
                URI.create(request.getRequestURI()),
                null);

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
