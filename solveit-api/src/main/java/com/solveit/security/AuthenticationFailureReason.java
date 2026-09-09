package com.solveit.security;

public enum AuthenticationFailureReason {
    TOKEN_INVALID,
    TOKEN_EXPIRED,
    ACCOUNT_INACTIVE;

    public static final String REQUEST_ATTRIBUTE = AuthenticationFailureReason.class.getName();
}
