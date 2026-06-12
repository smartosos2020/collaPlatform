package com.colla.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI collaOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Colla Platform API")
                .version("0.1.0")
                .description("Internal collaboration workspace API"));
    }
}

