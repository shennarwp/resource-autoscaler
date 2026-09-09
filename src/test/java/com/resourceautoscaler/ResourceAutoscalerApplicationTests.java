package com.resourceautoscaler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mock")
/** Tests the resource autoscaler application tests behavior and regression cases. */
class ResourceAutoscalerApplicationTests {

    /** Verifies context loads. */
    @Test
    void contextLoads() {
    }
}
