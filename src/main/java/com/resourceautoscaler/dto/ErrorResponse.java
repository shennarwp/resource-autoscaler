package com.resourceautoscaler.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Stable error envelope returned by all REST exception handlers. */
@Schema(name = "ErrorResponse", description = "Consistent API error response")
public record ErrorResponse(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "days must be less than or equal to 365") String message,
        @Schema(example = "2026-09-19T12:00:00Z") Instant timestamp
) {}
