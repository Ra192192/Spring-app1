package com.springapp1.auth_api.controller;

import com.springapp1.auth_api.dto.ProcessRequest;
import com.springapp1.auth_api.dto.ProcessResponse;
import com.springapp1.auth_api.service.ProcessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    @PostMapping("/process")
    public ProcessResponse process(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessRequest request
    ) {
        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject"
            );
        }

        return processService.process(userId, request);
    }
}