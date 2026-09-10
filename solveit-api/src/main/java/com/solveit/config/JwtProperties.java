package com.solveit.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "solveit.security.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience;

    @NotNull
    private Duration accessTokenTtl = Duration.ofHours(1);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    @AssertTrue(message = "must be valid Base64 representing at least 32 bytes")
    public boolean isSecretValid() {
        if (secret == null || secret.isBlank()) {
            return true;
        }

        try {
            return Decoders.BASE64.decode(secret).length >= 32;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @AssertTrue(message = "must be greater than zero")
    public boolean isAccessTokenTtlValid() {
        return accessTokenTtl == null
                || (!accessTokenTtl.isZero() && !accessTokenTtl.isNegative());
    }
}
