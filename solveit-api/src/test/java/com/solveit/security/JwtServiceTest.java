package com.solveit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T15:00:00Z");
    private static final String SIGNING_MATERIAL = "0123456789abcdef".repeat(4);
    private static final String OTHER_SIGNING_MATERIAL = "fedcba9876543210".repeat(4);

    @Test
    void issuesHs256TokenWithOnlyApprovedClaimsAndConfiguredTtl() {
        JwtService jwtService = serviceAt(NOW);

        String token = jwtService.issue(42L);
        Claims claims = jwtService.parse(token);
        Jws<Claims> signedClaims = Jwts.parser()
                .verifyWith(signingKey(SIGNING_MATERIAL))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token);

        assertThat(signedClaims.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plus(Duration.ofHours(1))));
        assertThat(claims.getIssuer()).isEqualTo("solveit-api");
        assertThat(claims.getAudience()).containsExactly("solveit-api");
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iat", "exp", "iss", "aud");
        assertThat(claims).doesNotContainKeys("nome", "email", "perfil", "ativo");
    }

    @Test
    void usesConfiguredTtlInsteadOfASecondHardCodedDuration() {
        JwtProperties properties = validProperties();
        properties.setAccessTokenTtl(Duration.ofMinutes(17));
        JwtService jwtService = new JwtService(properties, fixedClock(NOW));

        Claims claims = jwtService.parse(jwtService.issue(7L));

        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(17)));
    }

    @Test
    void rejectsWeakSigningKeyWhenServiceIsCreated() {
        JwtProperties properties = validProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                "short".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new JwtService(properties, fixedClock(NOW)))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = signedToken(
                signingKey(OTHER_SIGNING_MATERIAL),
                Jwts.SIG.HS256,
                "solveit-api",
                "solveit-api",
                NOW,
                NOW.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> serviceAt(NOW).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsSignatureAlgorithmOtherThanHs256() {
        String token = signedToken(
                signingKey(SIGNING_MATERIAL),
                Jwts.SIG.HS384,
                "solveit-api",
                "solveit-api",
                NOW,
                NOW.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> serviceAt(NOW).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnexpectedIssuer() {
        String token = signedToken(
                signingKey(SIGNING_MATERIAL),
                Jwts.SIG.HS256,
                "another-issuer",
                "solveit-api",
                NOW,
                NOW.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> serviceAt(NOW).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnexpectedAudience() {
        String token = signedToken(
                signingKey(SIGNING_MATERIAL),
                Jwts.SIG.HS256,
                "solveit-api",
                "another-api",
                NOW,
                NOW.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> serviceAt(NOW).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokenUsingInjectedClock() {
        String token = serviceAt(NOW).issue(42L);
        JwtService laterService = serviceAt(NOW.plus(Duration.ofHours(1)).plusSeconds(1));

        assertThatThrownBy(() -> laterService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void classifiesExpiredTokenSeparatelyForAuthentication() {
        String token = serviceAt(NOW).issue(42L);
        JwtService laterService = serviceAt(NOW.plus(Duration.ofHours(1)).plusSeconds(1));

        JwtService.AuthenticationResult result = laterService.parseForAuthentication(token);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.claims()).isNull();
        assertThat(result.failureReason()).isEqualTo(AuthenticationFailureReason.TOKEN_EXPIRED);
    }

    @Test
    void classifiesEveryOtherJwtRejectionAsInvalidForAuthentication() {
        String token = signedToken(
                signingKey(OTHER_SIGNING_MATERIAL),
                Jwts.SIG.HS256,
                "solveit-api",
                "solveit-api",
                NOW,
                NOW.plus(Duration.ofHours(1)));

        JwtService.AuthenticationResult result = serviceAt(NOW).parseForAuthentication(token);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.claims()).isNull();
        assertThat(result.failureReason()).isEqualTo(AuthenticationFailureReason.TOKEN_INVALID);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> serviceAt(NOW).parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(validProperties(), fixedClock(instant));
    }

    private JwtProperties validProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                SIGNING_MATERIAL.getBytes(StandardCharsets.UTF_8)));
        properties.setIssuer("solveit-api");
        properties.setAudience("solveit-api");
        properties.setAccessTokenTtl(Duration.ofHours(1));
        return properties;
    }

    private Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private SecretKey signingKey(String material) {
        return Keys.hmacShaKeyFor(material.getBytes(StandardCharsets.UTF_8));
    }

    private String signedToken(
            SecretKey key,
            io.jsonwebtoken.security.MacAlgorithm algorithm,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiration) {
        return Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .issuer(issuer)
                .audience().add(audience).and()
                .signWith(key, algorithm)
                .compact();
    }
}
