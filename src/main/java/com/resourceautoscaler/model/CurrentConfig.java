package com.resourceautoscaler.model;

/** Discovered or configured capacity values used for cost estimation and display. */
public record CurrentConfig(
    String resourceId,
    int replicas,
    int availableReplicas,
    double cpuRequestCores,
    double cpuLimitCores,
    double memoryRequestGiB,
    double memoryLimitGiB,
    int nodeCount,
    double nodeCpuCores,
    boolean available
) {
    /** Returns an explicitly unavailable configuration for failed discovery. */
    public static CurrentConfig unknown(String resourceId) {
        return new CurrentConfig(resourceId, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}