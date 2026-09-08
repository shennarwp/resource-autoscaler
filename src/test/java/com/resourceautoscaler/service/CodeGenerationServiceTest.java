package com.resourceautoscaler.service;

import com.resourceautoscaler.model.ScalingRecommendation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationServiceTest {

    private final CodeGenerationService service = new CodeGenerationService();

    @Test
    void kedaYamlCoversWeekdaysOffPeakAndWeekendScaling() {
        ScalingRecommendation rec = new ScalingRecommendation(
            "nginx-busy", "Nginx Busy (K3s)",
            ScalingRecommendation.ResourceType.AKS_DEPLOYMENT,
            ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT,
            "3 replicas, running 24/7", "KEDA ScaledObject",
            "07:00 - 18:00", "18:00 - 07:00",
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            100.0, 25.0, 0.8,
            Instant.now(), "rationale"
        );

        String yaml = service.generateKedaScaledObject(rec);

        assertEquals(3, countOccurrences(yaml, "- type: cron"));
        assertTrue(yaml.contains("desiredReplicas: \"3\""));
        assertTrue(yaml.contains("desiredReplicas: \"1\""));
        assertTrue(yaml.contains("start: \"07:00\""));
        assertTrue(yaml.contains("end: \"07:00\""));
        assertTrue(yaml.contains("start: \"18:00\""));
        assertTrue(yaml.contains("end: \"18:00\""));
        assertTrue(yaml.contains("days: \"Saturday,Sunday\""));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}