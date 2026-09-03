package com.resourceautoscaler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resourceAutoscalerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resource Autoscaler API")
                        .description("Cloud Cost & FinOps Optimization Platform — "
                                + "monitors Azure resource utilization and generates "
                                + "right-sizing and scheduling recommendations.")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("shennarwp")
                                .url("https://github.com/shennarwp/resource-autoscaler"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
