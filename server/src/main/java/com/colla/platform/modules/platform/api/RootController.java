package com.colla.platform.modules.platform.api;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
            "status", "ok",
            "service", "colla-platform-api",
            "health", "/api/health",
            "frontend", "http://localhost:5173",
            "time", OffsetDateTime.now().toString()
        );
    }
}
