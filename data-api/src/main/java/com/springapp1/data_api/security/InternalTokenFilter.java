package com.springapp1.data_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;

    public InternalTokenFilter(
            @Value("${internal.token}") String internalToken
    ) {
        if (internalToken.isBlank()) {
            throw new IllegalArgumentException(
                    "INTERNAL_TOKEN must not be blank"
            );
        }

        this.expectedToken =
                internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedToken = request.getHeader("X-Internal-Token");

        if (providedToken == null ||
                !MessageDigest.isEqual(
                        expectedToken,
                        providedToken.getBytes(StandardCharsets.UTF_8)
                )) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }
}