package com.resourceautoscaler.service;

import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

@Service
public class CodeGenerationService {

    public String generateKedaScaledObject(ScalingRecommendation recommendation) {
        String peakStart = formatCronTime(recommendation.peakStart());
        String peakEnd = formatCronTime(recommendation.peakEnd());

        return """
            apiVersion: keda.sh/v1alpha1
            kind: ScaledObject
            metadata:
              name: %s-scaler
              namespace: production
              labels:
                app: %s
                managed-by: resource-autoscaler
            spec:
              scaleTargetRef:
                name: %s
              minReplicaCount: 1
              maxReplicaCount: 5
              pollingInterval: 30
              cooldownPeriod: 300
              triggers:
                - type: cron
                  metadata:
                    timezone: UTC
                    start: "%s"
                    end: "%s"
                    desiredReplicas: "3"
                    days: "Monday,Tuesday,Wednesday,Thursday,Friday"
                - type: cron
                  metadata:
                    timezone: UTC
                    start: "00:00"
                    end: "23:59"
                    desiredReplicas: "1"
                    days: "Saturday,Sunday"
                - type: cpu
                  metricType: Utilization
                  value:
                    request: percent
                    value: "65"
            """.formatted(
                recommendation.resourceName().toLowerCase().replace(" ", "-"),
                recommendation.resourceName().toLowerCase().replace(" ", "-"),
                recommendation.resourceName().toLowerCase().replace(" ", "-"),
                peakStart,
                peakEnd
            );
    }

    public String generateTerraformAutoscale(ScalingRecommendation recommendation) {
        String resourceName = recommendation.resourceName().toLowerCase().replace(" ", "_");

        return """
            resource "azurerm_monitor_autoscale_setting" "%s_autoscale" {
              name                = "%s-autoscale"
              resource_group_name = azurerm_resource_group.main.name
              location            = azurerm_resource_group.main.location
              target_resource_id  = azurerm_%s.%s.id

              profile {
                name = "peak-hours"

                capacity {
                  minimum = "2"
                  maximum = "8"
                  default = "4"
                }

                rule {
                  metric_trigger {
                    metric_name        = "Percentage CPU"
                    metric_resource_id = azurerm_%s.%s.id
                    time_grain         = "PT1M"
                    statistic          = "Average"
                    time_window        = "PT5M"
                    time_aggregation   = "Average"
                    operator           = "GreaterThan"
                    threshold          = 65
                  }

                  scale_action {
                    direction = "Increase"
                    type      = "ChangeCount"
                    value     = "1"
                    cooldown  = "PT10M"
                  }
                }

                rule {
                  metric_trigger {
                    metric_name        = "Percentage CPU"
                    metric_resource_id = azurerm_%s.%s.id
                    time_grain         = "PT1M"
                    statistic          = "Average"
                    time_window        = "PT15M"
                    time_aggregation   = "Average"
                    operator           = "LessThan"
                    threshold          = 30
                  }

                  scale_action {
                    direction = "Decrease"
                    type      = "ChangeCount"
                    value     = "1"
                    cooldown  = "PT10M"
                  }
                }

                recurrence {
                  timezone = "UTC"
                  days     = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
                  hours    = [%d]
                  minutes  = [%d]
                }
              }

              profile {
                name = "off-peak-hours"

                capacity {
                  minimum = "1"
                  maximum = "2"
                  default = "1"
                }

                recurrence {
                  timezone = "UTC"
                  days     = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
                  hours    = [%d]
                  minutes  = [%d]
                }
              }
            }
            """.formatted(
                resourceName,
                resourceName,
                getTerraformResourceType(recommendation.resourceType()),
                resourceName,
                getTerraformResourceType(recommendation.resourceType()),
                resourceName,
                getTerraformResourceType(recommendation.resourceType()),
                resourceName,
                recommendation.peakStart().getHour(),
                recommendation.peakStart().getMinute(),
                recommendation.peakEnd().getHour(),
                recommendation.peakEnd().getMinute()
            );
    }

    private String formatCronTime(java.time.LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private String getTerraformResourceType(ScalingRecommendation.ResourceType type) {
        return switch (type) {
            case AZURE_VM -> "linux_virtual_machine";
            case AZURE_APP_SERVICE -> "app_service";
            case AZURE_FUNCTION -> "function_app";
            case AKS_DEPLOYMENT -> "kubernetes_cluster";
            default -> "linux_virtual_machine";
        };
    }
}
