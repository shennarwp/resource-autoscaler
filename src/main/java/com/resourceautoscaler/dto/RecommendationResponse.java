package com.resourceautoscaler.dto;

import com.resourceautoscaler.model.ScalingRecommendation;
import java.util.List;

public record RecommendationResponse(
    ScalingRecommendation recommendation,
    String kedaYaml,
    String terraformHcl
) {}
