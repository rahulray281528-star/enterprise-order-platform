package com.enterprise.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class GatewayJwtProperties {

    private String secret;
}
