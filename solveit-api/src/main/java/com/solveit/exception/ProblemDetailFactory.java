package com.solveit.exception;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailFactory {

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
}
