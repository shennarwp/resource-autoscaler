package com.resourceautoscaler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot entry point; caching and scheduled eviction are enabled here. */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ResourceAutoscalerApplication {

    /** Starts the API using the active Spring profile and command-line options. */
    static void main(String[] args) {
        SpringApplication.run(ResourceAutoscalerApplication.class, args);
    }
}
