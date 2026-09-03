package com.springapp1.auth_api.service;

import com.springapp1.auth_api.dto.LoginRequest;
import com.springapp1.auth_api.dto.LoginResponse;
import com.springapp1.auth_api.dto.RegisterRequest;
import com.springapp1.auth_api.entity.User;
import com.springapp1.auth_api.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String password = request.password();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(email, passwordHash);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Защита от одновременной регистрации одного email.
            if (userRepository.existsByEmail(email)) {
                throw emailAlreadyExists();
            }

            throw exception;
        }
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String password = request.password();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtService.generateToken(user.getId());

        return new LoginResponse(token);
    }

    private ResponseStatusException emailAlreadyExists() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email is already registered"
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );
    }
}