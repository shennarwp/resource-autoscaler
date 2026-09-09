package com.resourceautoscaler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resourceautoscaler.model.ScalingRecommendation;

/** Recommendation plus the provider-specific manifest generated for it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendationResponse(
    ScalingRecommendation recommendation,
    String kedaYaml,
    String terraformHcl
) {}
