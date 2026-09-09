package com.resourceautoscaler.service;

import com.resourceautoscaler.model.ScalingRecommendation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the code generation service behavior and regression cases. */
class CodeGenerationServiceTest {

    private final CodeGenerationService service = new CodeGenerationService();

    /** Verifies keda yaml uses supported cron and cpu metadata. */
    @Test
    void kedaYamlUsesSupportedCronAndCpuMetadata() {
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
        assertTrue(yaml.contains("kind: ScaledObject"));
        assertTrue(yaml.contains("scaleTargetRef:"));
        assertTrue(yaml.contains("type: cpu"));
        assertTrue(yaml.contains("type: Utilization"));
        assertTrue(yaml.contains("value: \"65\""));
        assertTrue(yaml.contains("days: \"Mon-Fri\""));
        assertTrue(yaml.contains("days: \"Sat-Sun\""));
        assertTrue(yaml.contains("start: \"07:00\""));
        assertTrue(yaml.contains("end: \"18:00\""));
    }

    /** Verifies app service autoscale uses service plan target. */
    @Test
    void appServiceAutoscaleUsesServicePlanTarget() {
        ScalingRecommendation rec = new ScalingRecommendation(
            "appservice-api-gateway", "API Gateway",
            ScalingRecommendation.ResourceType.AZURE_APP_SERVICE,
            ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING,
            "Standard S3 tier, always running", "Auto-scale: S3 07:00-18:00 UTC, B1 18:00-07:00 UTC",
            "07:00 - 18:00", "18:00 - 07:00",
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            120.0, 20.0, 0.75,
            Instant.now(), "rationale"
        );

        String hcl = service.generateAppServiceAutoscale(rec);

        assertTrue(hcl.contains("azurerm_monitor_autoscale_setting"));
        assertTrue(hcl.contains("target_resource_id  = azurerm_service_plan.api-gateway.id"));
        assertTrue(hcl.contains("days     = [\"Monday\", \"Tuesday\", \"Wednesday\", \"Thursday\", \"Friday\"]"));
        assertTrue(hcl.contains("hours    = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]"));
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