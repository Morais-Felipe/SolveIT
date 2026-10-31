package com.solveit.exception;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleParameterValidation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(
                        lastPathNode(violation.getPropertyPath()),
                        violation.getMessage()));

        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Requisição inválida",
                "Um ou mais parâmetros estão inválidos.",
                requestUri(request),
                errors);

        return problemResponse(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(exception.getName(), "O valor informado não tem o formato esperado");

        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Requisição inválida",
                "Um ou mais parâmetros estão inválidos.",
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
            LockTimeoutException.class,
            PessimisticLockException.class,
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

    /**
     * Última rede de segurança (plano 07-01 D-1). Três caminhos:
     * <ol>
     *   <li>401/403 do Spring Security são relançados sem alteração: a cadeia de filtros
     *       (ExceptionTranslationFilter) responde como sempre respondeu. Relançar a mesma
     *       exceção faz o Spring desistir deste handler em silêncio.</li>
     *   <li>Exceções que já trazem um status HTTP (ResponseStatusException, rota ou método
     *       inexistente) mantêm o status, no formato do SolveIT e com texto fixo.</li>
     *   <li>O resto é defeito: 500 com texto fixo e um errorId (D-2). O detalhe completo
     *       vai só para o log, junto com o mesmo errorId.</li>
     * </ol>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) throws Exception {
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }

        if (exception instanceof ErrorResponse errorResponse
                && !errorResponse.getStatusCode().is5xxServerError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            ProblemDetail problem = switch (status) {
                case NOT_FOUND -> problemDetailFactory.create(
                        status,
                        ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Recurso não encontrado",
                        "O recurso solicitado não existe.",
                        requestUri(request),
                        null);
                case METHOD_NOT_ALLOWED -> problemDetailFactory.create(
                        status,
                        ApiErrorCode.METHOD_NOT_ALLOWED,
                        "Método não permitido",
                        "Este recurso não aceita o método " + request.getMethod() + ".",
                        requestUri(request),
                        null);
                default -> problemDetailFactory.create(
                        status,
                        ApiErrorCode.REQUEST_INVALID,
                        "Requisição inválida",
                        "A requisição não pôde ser processada.",
                        requestUri(request),
                        null);
            };
            return problemResponse(status, errorResponse.getHeaders(), problem);
        }

        String errorId = UUID.randomUUID().toString().substring(0, 8);
        SAFE_ERROR_LOG.error(
                "Unexpected error {} on {} {}",
                errorId,
                request.getMethod(),
                request.getRequestURI(),
                exception);

        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente.",
                requestUri(request),
                null);
        problem.setProperty("errorId", errorId);

        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private static String lastPathNode(Path propertyPath) {
        String name = null;
        for (Path.Node node : propertyPath) {
            name = node.getName();
        }
        return name;
    }

    private static URI requestUri(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }

    private static ResponseEntity<ProblemDetail> problemResponse(
            HttpStatus status,
            ProblemDetail problem) {
        return problemResponse(status, HttpHeaders.EMPTY, problem);
    }

    /** Mantém cabeçalhos que o status exige, como o Allow de um 405. */
    private static ResponseEntity<ProblemDetail> problemResponse(
            HttpStatus status,
            HttpHeaders headers,
            ProblemDetail problem) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
