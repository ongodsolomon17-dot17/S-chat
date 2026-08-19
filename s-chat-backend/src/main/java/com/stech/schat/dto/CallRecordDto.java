package com.stech.schat.dto;

import com.stech.schat.model.CallRecord;
import java.time.Instant;
import java.util.UUID;

public record CallRecordDto(
        UUID id, UUID callerId, UUID calleeId,
        CallRecord.CallType callType, CallRecord.CallStatus status,
        Instant startedAt, Instant answeredAt, Instant endedAt
) {}
