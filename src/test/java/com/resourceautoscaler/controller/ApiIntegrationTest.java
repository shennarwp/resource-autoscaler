package com.resourceautoscaler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the mock profile's HTTP, validation, and JSON response flow end to end. */
@SpringBootTest(properties = {
        "spring.profiles.active=mock",
        "app.security.enabled=false",
        "app.rate-limit.requests-per-window=1000"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void metricsEndpointReturnsAggregatesAndPercentiles() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/nginx-busy").param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("nginx-busy"))
                .andExpect(jsonPath("$.stats.p50CpuUtilization").exists())
                .andExpect(jsonPath("$.stats.p95CpuUtilization").exists())
                .andExpect(jsonPath("$.stats.p99CpuUtilization").exists());
    }

    @Test
    void invalidMetricWindowReturnsDocumentedErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/nginx-busy").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
