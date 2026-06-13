package com.colla.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CollaPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollaPlatformApplication.class, args);
    }
}
