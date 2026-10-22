package com.solveit.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifica qual constraint UNIQUE do MySQL causou uma falha de integridade, para que
 * somente aquela violação específica vire um 409 controlado.
 */
public final class UniqueConstraints {

    public static final String USUARIO_EMAIL = "uk_usuario_email";
    public static final String CATEGORIA_NOME = "uk_categoria_nome";

    private UniqueConstraints() {
    }

    public static boolean isViolationOf(
            DataIntegrityViolationException exception,
            String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && matches(constraintViolation.getConstraintName(), constraintName)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    private static boolean matches(String reportedName, String expectedName) {
        if (reportedName == null) {
            return false;
        }
        String unquotedName = reportedName.replace("`", "").replace("\"", "");
        int lastQualifier = unquotedName.lastIndexOf('.');
        String simpleName = lastQualifier >= 0
                ? unquotedName.substring(lastQualifier + 1)
                : unquotedName;
        return expectedName.equalsIgnoreCase(simpleName);
    }
}
