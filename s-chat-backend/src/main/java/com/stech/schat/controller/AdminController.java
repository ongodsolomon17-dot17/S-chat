package com.stech.schat.controller;

import com.stech.schat.dto.AdminStatsDto;
import com.stech.schat.dto.ReportDto;
import com.stech.schat.dto.ResolveReportRequest;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.Role;
import com.stech.schat.model.User;
import com.stech.schat.model.UserReport;
import com.stech.schat.repository.ChatMessageRepository;
import com.stech.schat.repository.UserReportRepository;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

/** Moderation tools. Routes under /api/admin/** require ADMIN or SUPERADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ReportService reportService;
    private final UserRepository userRepository;
    private final ChatMessageRepository messageRepository;
    private final UserReportRepository reportRepository;
    private final com.stech.schat.websocket.WebSocketSessionRegistry sessionRegistry;

    public AdminController(ReportService reportService, UserRepository userRepository,
                           ChatMessageRepository messageRepository, UserReportRepository reportRepository,
                           com.stech.schat.websocket.WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.reportService = reportService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> stats() {
        return ResponseEntity.ok(new AdminStatsDto(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))),
                messageRepository.count(),
                reportRepository.countByStatus(UserReport.Status.OPEN)));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportDto>> reports(@RequestParam(defaultValue = "OPEN") String status) {
        UserReport.Status s;
        try { s = UserReport.Status.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown status"); }
        return ResponseEntity.ok(reportService.list(s));
    }

    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<ReportDto> resolve(Authentication auth, @PathVariable UUID id,
                                             @Valid @RequestBody ResolveReportRequest body) {
        UserReport.Status s;
        try { s = UserReport.Status.valueOf(body.action().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose RESOLVED or DISMISSED"); }
        return ResponseEntity.ok(reportService.resolve(currentUserId(auth), id, s));
    }

    @PostMapping("/users/{id}/suspend")
    @Transactional
    public ResponseEntity<Void> suspend(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.getRole() != Role.USER) throw new ForbiddenActionException("Admin accounts can't be suspended here");
        u.setAccountEnabled(false);
        u.setRefreshTokenVersion(u.getRefreshTokenVersion() + 1);
        userRepository.save(u);
        sessionRegistry.disconnect(u.getId(), "Account suspended");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/restore")
    @Transactional
    public ResponseEntity<Void> restore(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.isDeleted()) throw new IllegalArgumentException("Deleted accounts can't be restored");
        u.setAccountEnabled(true);
        userRepository.save(u);
        return ResponseEntity.noContent().build();
    }
}
