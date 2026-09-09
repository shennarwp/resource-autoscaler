package com.resourceautoscaler.service;

import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

@Service
public class CodeGenerationService {

    public String generateKedaScaledObject(ScalingRecommendation recommendation) {
        String resourceName = recommendation.resourceName().trim();
        String appName = resourceName.toLowerCase().replace(" ", "-");
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
                apiVersion: apps/v1
                kind: Deployment
                name: %s
              minReplicaCount: 1
              maxReplicaCount: 5
              cooldownPeriod: 300
              triggers:
                - type: cron
                  metadata:
                    timezone: UTC
                    start: "%s"
                    end: "%s"
                    desiredReplicas: "3"
                    days: "Mon-Fri"
                - type: cron
                  metadata:
                    timezone: UTC
                    start: "%s"
                    end: "23:59"
                    desiredReplicas: "1"
                    days: "Mon-Fri"
                - type: cron
                  metadata:
                    timezone: UTC
                    start: "00:00"
                    end: "23:59"
                    desiredReplicas: "1"
                    days: "Sat-Sun"
                - type: cpu
                  metricType: Utilization
                  metadata:
                    type: Utilization
                    value: "65"
            """.formatted(
                appName,
                appName,
                appName,
                peakStart,
                peakEnd,
                peakEnd,
                peakStart
            );
    }

    public String generateAppServiceAutoscale(ScalingRecommendation recommendation) {
        String resourceName = recommendation.resourceName().toLowerCase().replace(" ", "-");
        String peakHours = formatHourList(recommendation.peakStart().getHour(), recommendation.peakEnd().getHour());
        String offPeakHours = formatOffPeakHourList(recommendation.peakStart().getHour(), recommendation.peakEnd().getHour());

        return """
            resource "azurerm_monitor_autoscale_setting" "%s_autoscale" {
              name                = "%s-autoscale"
              resource_group_name = azurerm_resource_group.main.name
              location            = azurerm_resource_group.main.location
              target_resource_id  = azurerm_service_plan.%s.id

              profile {
                name = "peak-hours"

                capacity {
                  default = 2
                  minimum = 1
                  maximum = 4
                }

                rule {
                  metric_trigger {
                    metric_name        = "CpuPercentage"
                    metric_resource_id = azurerm_service_plan.%s.id
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
                    metric_name        = "CpuPercentage"
                    metric_resource_id = azurerm_service_plan.%s.id
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
                  hours    = [%s]
                  minutes  = [0]
                }
              }

              profile {
                name = "off-peak-hours"

                capacity {
                  default = 1
                  minimum = 1
                  maximum = 2
                }

                recurrence {
                  timezone = "UTC"
                  days     = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
                  hours    = [%s]
                  minutes  = [0]
                }
              }
            }
            """.formatted(
                resourceName,
                resourceName,
                resourceName,
                resourceName,
                resourceName,
                peakHours,
                offPeakHours
            );
    }

    public String generateTerraformAutoscale(ScalingRecommendation recommendation) {
        String resourceName = recommendation.resourceName().toLowerCase().replace(" ", "_");
        String resourceType = getTerraformResourceType(recommendation.resourceType());
        String unqualifiedType = switch (recommendation.resourceType()) {
            case AZURE_VM -> "linux_virtual_machine";
            case AZURE_APP_SERVICE -> "service_plan";
            case AZURE_FUNCTION -> "linux_function_app";
            case AKS_DEPLOYMENT -> "kubernetes_cluster";
            default -> "linux_virtual_machine";
        };

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
                resourceType,
                resourceName,
                resourceType,
                resourceName,
                resourceType,
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

    private String formatHourList(int startHour, int endHour) {
        if (startHour == endHour) {
            return "0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.min(startHour, endHour);
        int end = Math.max(startHour, endHour);
        for (int hour = start; hour < end; hour++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hour);
        }
        return sb.toString();
    }

    private String formatOffPeakHourList(int startHour, int endHour) {
        StringBuilder sb = new StringBuilder();
        for (int hour = 0; hour < 24; hour++) {
            if (hour >= startHour && hour < endHour) {
                continue;
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(hour);
        }
        return sb.toString();
    }

    private String getTerraformResourceType(ScalingRecommendation.ResourceType type) {
        return switch (type) {
            case AZURE_VM -> "linux_virtual_machine";
            case AZURE_APP_SERVICE -> "service_plan";
            case AZURE_FUNCTION -> "linux_function_app";
            case AKS_DEPLOYMENT -> "kubernetes_cluster";
            default -> "linux_virtual_machine";
        };
    }
}
