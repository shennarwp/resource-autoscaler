package com.resourceautoscaler.model;

/**
 * Infers the public resource type and display name from a resource's stable ID.
 * Centralizes the prefix conventions ("nginx-", "aks-", "vm-", "app-", "func-")
 * that were previously duplicated across every repository and service.
 */
public final class ResourceTypeResolver {

    private ResourceTypeResolver() {}

    /** Infers the resource type from the ID prefix conventions used across repositories. */
    public static String resourceType(String resourceId) {
        if (resourceId == null) return "UNKNOWN";
        if (resourceId.startsWith("nginx")) return "K8S_CLUSTER";
        if (resourceId.startsWith("aks")) return "AKS_CLUSTER";
        if (resourceId.startsWith("vm")) return "AZURE_VM";
        if (resourceId.startsWith("app")) return "APP_SERVICE";
        if (resourceId.startsWith("func")) return "AZURE_FUNCTION";
        if (resourceId.startsWith("autoscaler")) return "APP_SERVICE";
        return "UNKNOWN";
    }

    /** Provides a friendly display name for known resource IDs, otherwise the ID itself. */
    public static String displayName(String resourceId) {
        return switch (resourceId) {
            case "nginx-busy" -> "Nginx Busy (K3s)";
            case "nginx-idle" -> "Nginx Idle (K3s)";
            case "aks-primary-cluster" -> "Primary AKS Cluster";
            case "vm-backend-01" -> "Backend VM-01";
            case "appservice-api-gateway" -> "API Gateway";
            case "function-data-processor" -> "Data Processor Function";
            default -> resourceId;
        };
    }
}