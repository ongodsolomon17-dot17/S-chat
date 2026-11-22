package com.stech.schat.controller;

import com.stech.schat.dto.ReportRequest;
import com.stech.schat.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<Void> report(Authentication auth, @Valid @RequestBody ReportRequest request) {
        reportService.create(currentUserId(auth), request);
        return ResponseEntity.noContent().build();
    }
}
