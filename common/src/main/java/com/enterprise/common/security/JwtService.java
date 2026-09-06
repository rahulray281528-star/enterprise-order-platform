package com.enterprise.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JWTs. Written against the jjwt 0.12.x API.
 *
 * <p>The same class is used by the services that issue tokens (auth-service) and by every
 * service that verifies them, so a token can never be signed one way and read another.</p>
 */
@Service
@Slf4j
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be set and at least 32 bytes long. Set the JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String username, String role) {
        return build(userId, Map.of(
                CLAIM_ROLE, role,
                CLAIM_USERNAME, username,
                CLAIM_TYPE, TYPE_ACCESS), properties.getExpiration());
    }

    public String generateRefreshToken(String userId, String username) {
        return build(userId, Map.of(
                CLAIM_USERNAME, username,
                CLAIM_TYPE, TYPE_REFRESH), properties.getRefreshExpiration());
    }

    private String build(String subject, Map<String, Object> claims, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** @return the verified claims, or null when the token is absent, tampered with or expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    public boolean isValid(String token) {
        return parse(token) != null;
    }

    public String getUserId(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    public String getRole(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.get(CLAIM_ROLE, String.class);
    }

    public long getExpirationSeconds() {
        return properties.getExpiration() / 1000;
    }
}
