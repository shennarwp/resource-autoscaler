package com.resourceautoscaler.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResourceMetricsCacheEvictor {

    @Scheduled(fixedDelayString = "${app.cache.eviction-minutes:5}m")
    @CacheEvict(value = "resourceMetrics", allEntries = true)
    public void evictResourceMetricsCache() {
    }
}