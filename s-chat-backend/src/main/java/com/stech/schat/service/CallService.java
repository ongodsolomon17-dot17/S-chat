package com.stech.schat.service;

import com.stech.schat.dto.CallRecordDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.CallRecord;
import com.stech.schat.repository.CallRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CallService {
    private final CallRecordRepository repository;
    private final FriendService friendService;

    public CallService(CallRecordRepository repository, FriendService friendService) {
        this.repository = repository;
        this.friendService = friendService;
    }

    @Transactional
    public CallRecordDto start(UUID callerId, UUID calleeId, CallRecord.CallType type) {
        if (callerId.equals(calleeId)) throw new IllegalArgumentException("You cannot call yourself");
        requireFriends(callerId, calleeId);
        CallRecord call = CallRecord.builder()
                .callerId(callerId).calleeId(calleeId).callType(type)
                .status(CallRecord.CallStatus.RINGING).build();
        return toDto(repository.save(call));
    }

    @Transactional
    public CallRecordDto accept(UUID userId, UUID callId) {
        CallRecord call = requireParticipant(userId, callId);
        if (!call.getCalleeId().equals(userId))
            throw new ForbiddenActionException("Only the recipient can accept this call");
        if (call.getStatus() == CallRecord.CallStatus.RINGING) {
            if (call.getStartedAt().isBefore(Instant.now().minusSeconds(90))) {
                call.setStatus(CallRecord.CallStatus.MISSED);
                call.setEndedAt(Instant.now());
                repository.save(call);
                throw new IllegalArgumentException("This call has expired");
            }
            call.setStatus(CallRecord.CallStatus.ACCEPTED);
            call.setAnsweredAt(Instant.now());
            repository.save(call);
        }
        return toDto(call);
    }

    @Transactional
    public CallRecordDto reject(UUID userId, UUID callId) {
        CallRecord call = requireParticipant(userId, callId);
        if (!call.getCalleeId().equals(userId))
            throw new ForbiddenActionException("Only the recipient can reject this call");
        if (call.getStatus() == CallRecord.CallStatus.RINGING) {
            call.setStatus(CallRecord.CallStatus.REJECTED);
            call.setEndedAt(Instant.now());
            repository.save(call);
        }
        return toDto(call);
    }

    @Transactional
    public CallRecordDto end(UUID userId, UUID callId) {
        CallRecord call = requireParticipant(userId, callId);
        if (call.getStatus() == CallRecord.CallStatus.RINGING) call.setStatus(CallRecord.CallStatus.MISSED);
        else if (call.getStatus() == CallRecord.CallStatus.ACCEPTED) call.setStatus(CallRecord.CallStatus.ENDED);
        if (call.getEndedAt() == null) call.setEndedAt(Instant.now());
        return toDto(repository.save(call));
    }

    @Transactional(readOnly = true)
    public CallRecordDto requireParticipantForSignal(UUID userId, UUID callId) {
        CallRecord call = requireParticipant(userId, callId);
        if (call.getStatus() != CallRecord.CallStatus.ACCEPTED) {
            throw new ForbiddenActionException("Call signaling is only allowed after acceptance");
        }
        return toDto(call);
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireStaleRingingCalls() {
        Instant cutoff = Instant.now().minusSeconds(90);
        List<CallRecord> stale = repository.findByStatusAndStartedAtBefore(CallRecord.CallStatus.RINGING, cutoff);
        if (stale.isEmpty()) return;
        Instant now = Instant.now();
        stale.forEach(call -> {
            call.setStatus(CallRecord.CallStatus.MISSED);
            call.setEndedAt(now);
        });
        repository.saveAll(stale);
    }

    @Transactional(readOnly = true)
    public List<CallRecordDto> history(UUID userId, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return repository.findHistory(userId, PageRequest.of(0, safeSize))
                .stream().map(this::toDto).toList();
    }

    private CallRecord requireParticipant(UUID userId, UUID callId) {
        CallRecord call = repository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found"));
        if (!call.getCallerId().equals(userId) && !call.getCalleeId().equals(userId))
            throw new ForbiddenActionException("You do not have access to this call");
        return call;
    }

    private void requireFriends(UUID a, UUID b) {
        if (!friendService.areFriends(a, b))
            throw new ForbiddenActionException("You can only call users you're friends with");
    }

    private CallRecordDto toDto(CallRecord c) {
        return new CallRecordDto(c.getId(), c.getCallerId(), c.getCalleeId(), c.getCallType(),
                c.getStatus(), c.getStartedAt(), c.getAnsweredAt(), c.getEndedAt());
    }
}
