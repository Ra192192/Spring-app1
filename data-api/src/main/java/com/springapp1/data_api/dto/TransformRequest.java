package com.springapp1.data_api.dto;

import jakarta.validation.constraints.NotBlank;

public record TransformRequest(
        @NotBlank
        String text
) {
}