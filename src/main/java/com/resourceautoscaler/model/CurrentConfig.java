package com.resourceautoscaler.model;

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
    public static CurrentConfig unknown(String resourceId) {
        return new CurrentConfig(resourceId, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}