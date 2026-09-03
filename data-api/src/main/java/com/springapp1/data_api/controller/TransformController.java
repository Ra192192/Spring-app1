package com.springapp1.data_api.controller;

import com.springapp1.data_api.dto.TransformRequest;
import com.springapp1.data_api.dto.TransformResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class TransformController {

    private final String internalToken;

    public TransformController(@Value("${internal.token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @PostMapping("/transform")
    public TransformResponse transform(
            @RequestHeader(value = "X-Internal-Token", required = false)
            String providedToken,
            @Valid @RequestBody TransformRequest request
    ) {
        if (!internalToken.equals(providedToken)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal token"
            );
        }

        String result = request.text().toUpperCase(Locale.ROOT);
        return new TransformResponse(result);
    }
}