package com.stech.schat.service;

import com.stech.schat.dto.ReportDto;
import com.stech.schat.dto.ReportRequest;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.RateLimitExceededException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.ChatMessage;
import com.stech.schat.model.User;
import com.stech.schat.model.UserReport;
import com.stech.schat.repository.ChatMessageRepository;
import com.stech.schat.repository.UserReportRepository;
import com.stech.schat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReportService {

    private static final int MAX_REPORTS_PER_DAY = 10;

    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository messageRepository;

    public ReportService(UserReportRepository reportRepository, UserRepository userRepository,
                         ChatMessageRepository messageRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void create(UUID reporterId, ReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw new ForbiddenActionException("You can't report yourself");
        }
        userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.messageId() != null) {
            ChatMessage m = messageRepository.findById(request.messageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
            boolean involvesBoth =
                    (m.getSenderId().equals(reporterId) && m.getReceiverId().equals(request.reportedUserId()))
                 || (m.getSenderId().equals(request.reportedUserId()) && m.getReceiverId().equals(reporterId));
            if (!involvesBoth) {
                throw new ForbiddenActionException("You can only report messages from your own conversations");
            }
        }
        long recent = reportRepository.countByReporterIdAndCreatedAtAfter(reporterId, Instant.now().minus(Duration.ofDays(1)));
        if (recent >= MAX_REPORTS_PER_DAY) {
            throw new RateLimitExceededException("You have reached the daily report limit. Please try again tomorrow.");
        }
        String details = request.details() == null || request.details().isBlank() ? null : request.details().trim();
        reportRepository.save(UserReport.builder()
                .reporterId(reporterId)
                .reportedUserId(request.reportedUserId())
                .messageId(request.messageId())
                .reason(request.reason())
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public java.util.List<ReportDto> list(UserReport.Status status) {
        return reportRepository.findByStatusOrderByCreatedAtDesc(status, org.springframework.data.domain.PageRequest.of(0, 100))
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ReportDto resolve(UUID adminId, UUID reportId, UserReport.Status newStatus) {
        if (newStatus == UserReport.Status.OPEN) throw new IllegalArgumentException("Choose RESOLVED or DISMISSED");
        UserReport r = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        r.setStatus(newStatus);
        r.setResolvedAt(Instant.now());
        r.setResolvedBy(adminId);
        reportRepository.save(r);
        return toDto(r);
    }

    private ReportDto toDto(UserReport r) {
        String reporter = userRepository.findById(r.getReporterId()).map(User::getUsername).orElse("unknown");
        String reported = userRepository.findById(r.getReportedUserId()).map(User::getUsername).orElse("unknown");
        String excerpt = null;
        if (r.getMessageId() != null) {
            excerpt = messageRepository.findById(r.getMessageId()).map(m -> {
                String c = m.getContent() == null ? "" : m.getContent();
                return c.length() > 300 ? c.substring(0, 300) + "…" : c;
            }).orElse(null);
        }
        return new ReportDto(r.getId(), r.getReporterId(), reporter, r.getReportedUserId(), reported,
                r.getMessageId(), excerpt, r.getReason().name(), r.getDetails(), r.getStatus().name(), r.getCreatedAt());
    }
}
