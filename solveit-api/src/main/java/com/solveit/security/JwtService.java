package com.solveit.security;

import com.solveit.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.time.Clock;

public class JwtService {

    public JwtService(JwtProperties properties, Clock clock) {
    }

    public String issue(Long usuarioId) {
        throw new UnsupportedOperationException("JWT emission is not implemented yet");
    }

    public Claims parse(String token) {
        throw new UnsupportedOperationException("JWT parsing is not implemented yet");
    }
}
