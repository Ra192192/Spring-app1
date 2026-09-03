package com.springapp1.auth_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessRequest(
        @NotBlank
        @Size(max = 10000)
        String text
) {
}