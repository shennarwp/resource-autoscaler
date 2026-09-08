package com.resourceautoscaler.controller;

import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsControllerTest {

    @Test
    void peakConfigEndpointReturnsResourceSpecificConfig() {
        PeakHoursConfig functionConfig = new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10
        );
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        when(metricsService.getPeakHoursConfig("function-data-processor")).thenReturn(functionConfig);
        MetricsController controller = new MetricsController(metricsService);

        PeakHoursConfig result = controller.getPeakHoursConfig("function-data-processor").getBody();

        assertEquals(functionConfig, result);
    }
}