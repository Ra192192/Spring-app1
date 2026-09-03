package com.springapp1.auth_api;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private static final String JWT_SECRET =
            "test-only-jwt-secret-at-least-32-bytes-long";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("auth_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("db/001-create-tables.sql");

    private static final AtomicReference<String> receivedToken =
            new AtomicReference<>();

    private static final AtomicReference<String> receivedBody =
            new AtomicReference<>();

    private static final AtomicReference<String> receivedMethod =
            new AtomicReference<>();

    private static HttpServer dataApiStub;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    private static final AtomicInteger stubStatus = new AtomicInteger(200);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry)
            throws IOException {

        startDataApiStub();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("jwt.expiration-seconds", () -> 3600);

        registry.add("data-api.internal-token", () -> INTERNAL_TOKEN);
        registry.add(
                "data-api.base-url",
                () -> "http://127.0.0.1:" + dataApiStub.getAddress().getPort()
        );
    }

    private static void startDataApiStub() throws IOException {
        dataApiStub = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        dataApiStub.createContext("/api/transform", exchange -> {
            try {
                receivedMethod.set(exchange.getRequestMethod());
                receivedToken.set(
                        exchange.getRequestHeaders()
                                .getFirst("X-Internal-Token")
                );
                receivedBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                ));

                byte[] response = """
                        {"result":"HELLO"}
                        """.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json"
                );
                exchange.sendResponseHeaders(stubStatus.get(), response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });

        dataApiStub.start();
    }

    @BeforeEach
    void resetStubState() {
        jdbcTemplate.update("DELETE FROM processing_log");
        jdbcTemplate.update("DELETE FROM users");

        receivedToken.set(null);
        receivedBody.set(null);
        receivedMethod.set(null);
        stubStatus.set(200);
    }

    @AfterAll
    static void stopDataApiStub() {
        if (dataApiStub != null) {
            dataApiStub.stop(0);
        }
    }

    @Autowired
    JwtDecoder jwtDecoder;

    private String registerAndLogin() throws Exception {
        String authJson = """
            {
              "email": "test-%s@example.com",
              "password": "test-password"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.token");
    }

    private void assertNoProcessingLogs() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_log",
                Long.class
        );

        assertThat(count).isZero();
    }

    private void assertDataApiNotCalled() {
        assertThat(receivedMethod.get()).isNull();
        assertThat(receivedToken.get()).isNull();
        assertThat(receivedBody.get()).isNull();
    }

    private String createTestToken(
            String userId,
            String secret,
            Instant issuedAt,
            Instant expiresAt
    ) {
        var key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-api")
                .subject(userId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }



    @Test
    void registersLogsInProcessesAndSavesLog() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "test-password";

        String authJson = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class,
                email
        );

        assertThat(passwordHash).isNotEqualTo(password);
        assertThat(passwordEncoder)
                .isInstanceOf(
                        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class
                );
        assertThat(passwordEncoder.matches(password, passwordHash)).isTrue();

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(loginResponse, "$.token");

        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"text":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("HELLO"));

        assertThat(receivedMethod.get()).isEqualTo("POST");
        assertThat(receivedToken.get()).isEqualTo(INTERNAL_TOKEN);

        String forwardedText = JsonPath.read(receivedBody.get(), "$.text");
        assertThat(forwardedText).isEqualTo("hello");

        var logs = jdbcTemplate.queryForList(
                """
                SELECT p.input_text, p.output_text, p.created_at
                FROM processing_log p
                JOIN users u ON u.id = p.user_id
                WHERE u.email = ?
                """,
                email
        );

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("input_text")).isEqualTo("hello");
        assertThat(logs.getFirst().get("output_text")).isEqualTo("HELLO");
        assertThat(logs.getFirst().get("created_at")).isNotNull();
    }

    @Test
    void rejectsDuplicateRegistration() throws Exception {
        String authJson = """
            {
              "email": "duplicate@example.com",
              "password": "test-password"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(authJson))
                .andExpect(status().isConflict());

        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Long.class,
                "duplicate@example.com"
        );

        assertThat(userCount).isEqualTo(1L);
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "email": "login@example.com",
                              "password": "correct-password"
                            }
                            """))
                .andExpect(status().isCreated());

        var response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "email": "login@example.com",
                              "password": "wrong-password"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        String responseBody = response.getContentAsString();

        if (!responseBody.isBlank()) {
            java.util.Map<String, Object> errorBody =
                    JsonPath.read(responseBody, "$");

            assertThat(errorBody).doesNotContainKey("token");
        }
    }

    @Test
    void rejectsProcessWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/process")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"text":"hello"}
                            """))
                .andExpect(status().isUnauthorized());

        assertThat(receivedMethod.get()).isNull();
        assertThat(receivedToken.get()).isNull();
        assertThat(receivedBody.get()).isNull();

        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_log",
                Long.class
        );

        assertThat(logCount).isZero();
    }

    @Test
    void rejectsJwtWithWrongSignature() throws Exception {
        String validToken = registerAndLogin();
        String userId = jwtDecoder.decode(validToken).getSubject();

        Instant now = Instant.now();

        String wrongToken = createTestToken(
                userId,
                "different-test-secret-at-least-32-bytes-long",
                now,
                now.plusSeconds(3600)
        );

        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + wrongToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"text":"hello"}
                            """))
                .andExpect(status().isUnauthorized());

        assertDataApiNotCalled();
        assertNoProcessingLogs();
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        String validToken = registerAndLogin();
        String userId = jwtDecoder.decode(validToken).getSubject();

        Instant now = Instant.now();

        String expiredToken = createTestToken(
                userId,
                JWT_SECRET,
                now.minusSeconds(3600),
                now.minusSeconds(600)
        );

        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"text":"hello"}
                            """))
                .andExpect(status().isUnauthorized());

        assertDataApiNotCalled();
        assertNoProcessingLogs();
    }

    @Test
    void rejectsBlankText() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"text":"   "}
                            """))
                .andExpect(status().isBadRequest());

        assertDataApiNotCalled();
        assertNoProcessingLogs();
    }

    @Test
    void returnsBadGatewayWhenDataApiFails() throws Exception {
        String token = registerAndLogin();

        stubStatus.set(500);

        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"text":"hello"}
                            """))
                .andExpect(status().isBadGateway());

        assertThat(receivedMethod.get()).isEqualTo("POST");
        assertThat(receivedToken.get()).isEqualTo(INTERNAL_TOKEN);

        String text = JsonPath.read(receivedBody.get(), "$.text");
        assertThat(text).isEqualTo("hello");

        assertNoProcessingLogs();
    }
}