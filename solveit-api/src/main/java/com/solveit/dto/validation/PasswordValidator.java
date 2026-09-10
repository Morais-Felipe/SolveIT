package com.solveit.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public final class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MINIMUM_CODE_POINTS = 15;
    private static final int MAXIMUM_UTF8_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        int codePointCount = password.codePointCount(0, password.length());
        int utf8ByteCount = password.getBytes(StandardCharsets.UTF_8).length;

        return codePointCount >= MINIMUM_CODE_POINTS && utf8ByteCount <= MAXIMUM_UTF8_BYTES;
    }
}
