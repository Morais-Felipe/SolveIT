package com.solveit.security;

import com.solveit.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .clock(() -> Date.from(clock.instant()))
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build();
    }

    public String issue(Long usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId must not be null");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());

        return Jwts.builder()
                .subject(usuarioId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();

        if (issuedAt == null || expiration == null) {
            throw new MalformedJwtException("Required temporal claim is missing");
        }
        if (!expiration.after(issuedAt)) {
            throw new MalformedJwtException("Token expiration must be after issuance");
        }

        return claims;
    }

    AuthenticationResult parseForAuthentication(String token) {
        try {
            return AuthenticationResult.accepted(parse(token));
        } catch (ExpiredJwtException ignored) {
            return AuthenticationResult.rejected(AuthenticationFailureReason.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ignored) {
            return AuthenticationResult.rejected(AuthenticationFailureReason.TOKEN_INVALID);
        }
    }

    record AuthenticationResult(
            Claims claims,
            AuthenticationFailureReason failureReason) {

        private static AuthenticationResult accepted(Claims claims) {
            return new AuthenticationResult(
                    Objects.requireNonNull(claims, "claims must not be null"),
                    null);
        }

        private static AuthenticationResult rejected(AuthenticationFailureReason failureReason) {
            return new AuthenticationResult(
                    null,
                    Objects.requireNonNull(failureReason, "failureReason must not be null"));
        }

        boolean isAccepted() {
            return claims != null;
        }
    }
}
