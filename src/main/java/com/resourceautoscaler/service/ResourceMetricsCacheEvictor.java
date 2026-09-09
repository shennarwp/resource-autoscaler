package com.resourceautoscaler.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically invalidates cached metric windows so charts receive fresh samples. */
@Component
public class ResourceMetricsCacheEvictor {

    /** Clears every resource window after the configured fixed delay. */
    @Scheduled(fixedDelayString = "${app.cache.eviction-minutes:5}m")
    @CacheEvict(value = "resourceMetrics", allEntries = true)
    public void evictResourceMetricsCache() {
    }
}