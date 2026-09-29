package com.solveit.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.transaction.TransactionTimedOutException;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger SAFE_ERROR_LOG = LoggerFactory.getLogger("com.solveit.error");

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequestBody(
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_BODY_INVALID,
                "Corpo da requisição inválido",
                "O corpo da requisição contém JSON inválido.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ApiErrorCode.MEDIA_TYPE_NOT_SUPPORTED,
                "Formato de conteúdo não suportado",
                "Use Content-Type application/json.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, problem);
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_CREDENTIALS_INVALID,
                "Credenciais inválidas",
                "E-mail ou senha inválidos.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.UNAUTHORIZED, problem);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(
            DomainException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                exception.getStatus(),
                exception.getCode(),
                exception.getTitle(),
                exception.getDetail(),
                requestUri(request),
                null);

        return problemResponse(exception.getStatus(), problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.CONFLICT,
                ApiErrorCode.STALE_RESOURCE,
                "Recurso desatualizado",
                "O recurso foi alterado por outra operação. Atualize os dados e tente novamente.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.CONFLICT, problem);
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            QueryTimeoutException.class,
            TransactionTimedOutException.class
    })
    public ResponseEntity<ProblemDetail> handleCoreBusy(HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.CORE_BUSY,
                "Operação temporariamente indisponível",
                "Não foi possível iniciar a operação. Tente novamente.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            HttpServletRequest request) {
        SAFE_ERROR_LOG.error("Request failed with code {}", ApiErrorCode.INTERNAL_ERROR);

        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "Erro interno",
                "Não foi possível concluir a operação.",
                requestUri(request),
                null);

        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
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
