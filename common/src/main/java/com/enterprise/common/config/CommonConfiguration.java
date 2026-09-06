package com.enterprise.common.config;

import com.enterprise.common.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Picked up by every service because each application class scans the com.enterprise
 * base package. Binds the shared configuration properties.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonConfiguration {
}
