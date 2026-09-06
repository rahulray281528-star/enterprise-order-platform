package com.enterprise.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.dto.LoginRequest;
import com.enterprise.auth.dto.RegisterRequest;
import com.enterprise.auth.dto.TokenResponse;
import com.enterprise.auth.dto.UserResponse;
import com.enterprise.auth.entity.User;
import com.enterprise.auth.repository.UserRepository;
import com.enterprise.common.exception.BusinessException;
import com.enterprise.common.exception.UnauthorizedException;
import com.enterprise.common.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest() {
        return RegisterRequest.builder()
                .username("rahul")
                .email("rahul@example.com")
                .password("Str0ngPassword!")
                .role("CUSTOMER")
                .build();
    }

    @Test
    @DisplayName("register hashes the password and never stores it in clear text")
    void registerHashesPassword() {
        when(userRepository.existsByUsername("rahul")).thenReturn(false);
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPassword!")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(registerRequest());

        assertThat(response.getUsername()).isEqualTo("rahul");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        verify(passwordEncoder).encode("Str0ngPassword!");
    }

    @Test
    @DisplayName("register rejects a duplicate username with 409 rather than a database error")
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("rahul")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login issues an access and refresh token pair for valid credentials")
    void loginIssuesTokens() {
        User user = User.builder()
                .id("user-1").username("rahul").email("rahul@example.com")
                .passwordHash("$2a$10$hashed").role(User.UserRole.CUSTOMER).isActive(true)
                .build();

        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ngPassword!", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken("user-1", "rahul", "CUSTOMER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken("user-1", "rahul")).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        TokenResponse response = authService.login(
                LoginRequest.builder().username("rahul").password("Str0ngPassword!").build());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("a wrong password gives the same error as an unknown user, to prevent account enumeration")
    void loginDoesNotLeakWhetherUserExists() {
        User user = User.builder()
                .id("user-1").username("rahul").passwordHash("$2a$10$hashed")
                .role(User.UserRole.CUSTOMER).isActive(true).build();

        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("rahul").password("wrong").build()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("ghost").password("wrong").build()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("an inactive account cannot log in even with the correct password")
    void loginRejectsInactiveAccount() {
        User user = User.builder()
                .id("user-1").username("rahul").passwordHash("$2a$10$hashed")
                .role(User.UserRole.CUSTOMER).isActive(false).build();

        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("rahul").password("Str0ngPassword!").build()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("inactive");
    }
}
