package com.resourceautoscaler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ResourceAutoscalerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceAutoscalerApplication.class, args);
    }
}
