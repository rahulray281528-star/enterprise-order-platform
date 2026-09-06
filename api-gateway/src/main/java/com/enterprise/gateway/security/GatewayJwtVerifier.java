package com.enterprise.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verifies tokens at the edge. Uses the same secret and algorithm as the shared
 * JwtService in the common module - the gateway just cannot depend on that module
 * because common is servlet based and the gateway is reactive.
 */
@Component
@Slf4j
public class GatewayJwtVerifier {

    private final SecretKey key;

    public GatewayJwtVerifier(GatewayJwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be set and at least 32 bytes long. Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims verify(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Gateway rejected token: {}", ex.getMessage());
            return null;
        }
    }
}
