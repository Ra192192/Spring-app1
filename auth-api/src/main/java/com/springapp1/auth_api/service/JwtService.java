package com.springapp1.auth_api.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secret.isBlank() || secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must contain at least 32 UTF-8 bytes"
            );
        }

        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be positive"
            );
        }

        var secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");

        this.jwtEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(secretKey)
        );
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UUID userId) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-api")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}