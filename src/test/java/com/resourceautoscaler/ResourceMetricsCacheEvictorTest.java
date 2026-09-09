package com.resourceautoscaler;

import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.service.MetricsCollectionService;
import com.resourceautoscaler.service.ResourceMetricsCacheEvictor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
/* Tests the resource metrics cache evictor behavior and regression cases. */
class ResourceMetricsCacheEvictorTest {

    @Autowired
    private MetricsCollectionService metricsService;

    @Autowired
    private ResourceMetricsCacheEvictor cacheEvictor;

    /** Verifies cached snapshot is reused until evicted. */
    @Test
    void cachedSnapshotIsReusedUntilEvicted() {
        ResourceMetrics first = metricsService.collectMetrics("aks-primary-cluster", 30);
        ResourceMetrics second = metricsService.collectMetrics("aks-primary-cluster", 30);

        assertSame(first, second);

        cacheEvictor.evictResourceMetricsCache();

        ResourceMetrics refreshed = metricsService.collectMetrics("aks-primary-cluster", 30);
        assertNotSame(second, refreshed);
    }
}