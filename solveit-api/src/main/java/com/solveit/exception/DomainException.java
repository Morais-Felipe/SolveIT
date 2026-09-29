package com.solveit.exception;

import org.springframework.http.HttpStatus;

public final class DomainException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    public DomainException(
            ApiErrorCode code,
            HttpStatus status,
            String title,
            String detail) {
        super(code.name());
        this.code = code;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }
}
