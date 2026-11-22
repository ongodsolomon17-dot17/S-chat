package com.stech.schat.controller;

import com.stech.schat.config.FailoverDataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lets the frontend show a "read-only mode" banner while the fallback database is serving traffic. */
@RestController
@RequestMapping("/api/public")
public class StatusController {

    private final FailoverDataSource dataSource;

    public StatusController(FailoverDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean fallback = dataSource.isUsingFallback();
        return ResponseEntity.ok(Map.of("database", fallback ? "fallback" : "primary"));
    }
}
