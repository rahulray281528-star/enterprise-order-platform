package com.enterprise.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret is never hardcoded in source - it is supplied through
 * JWT_SECRET (see .env.example) and must be at least 32 characters for HS256.
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expiration = 3_600_000L;
    private long refreshExpiration = 86_400_000L;
    private String issuer = "enterprise-order-platform";
}
