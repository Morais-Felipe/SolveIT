package com.solveit.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Requisição inválida",
                "Um ou mais campos estão inválidos.",
                requestUri(request),
                errors);

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyRegistered(
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.CONFLICT,
                ApiErrorCode.EMAIL_ALREADY_REGISTERED,
                "Conflito de cadastro",
                "E-mail já cadastrado",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.CONFLICT, problem);
    }

    private static URI requestUri(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }

    private static ResponseEntity<ProblemDetail> problemResponse(
            HttpStatus status,
            ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
