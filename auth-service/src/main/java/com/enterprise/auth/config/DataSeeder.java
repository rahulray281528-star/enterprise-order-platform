package com.enterprise.auth.config;

import com.enterprise.auth.entity.User;
import com.enterprise.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the two demo accounts documented in the README so a reviewer can log in
 * immediately after {@code docker compose up}.
 *
 * <p>Hashes are produced by the application's own encoder rather than pasted into a
 * migration, so the seed can never drift from the encoder the login path uses.
 * Only active outside production profiles.</p>
 */
@Configuration
@Profile({"local", "docker", "default"})
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedUsers(UserRepository repository, PasswordEncoder encoder) {
        return args -> {
            seed(repository, encoder, "admin", "admin@enterprise.local", "Admin@123", User.UserRole.ADMIN);
            seed(repository, encoder, "customer", "customer@enterprise.local", "Customer@123",
                    User.UserRole.CUSTOMER);
        };
    }

    private void seed(UserRepository repository, PasswordEncoder encoder,
                      String username, String email, String rawPassword, User.UserRole role) {
        if (repository.existsByUsername(username)) {
            return;
        }
        repository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(encoder.encode(rawPassword))
                .role(role)
                .build());
        log.info("Seeded demo {} account: {}", role, username);
    }
}
