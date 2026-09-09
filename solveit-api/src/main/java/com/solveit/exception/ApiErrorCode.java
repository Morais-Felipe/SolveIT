package com.solveit.exception;

import java.net.URI;
import java.util.Locale;

public enum ApiErrorCode {
    VALIDATION_ERROR,
    EMAIL_ALREADY_REGISTERED,
    AUTH_CREDENTIALS_INVALID,
    INTERNAL_ERROR;

    public URI type() {
        String slug = name().toLowerCase(Locale.ROOT).replace('_', '-');
        return URI.create("urn:solveit:error:" + slug);
    }
}
