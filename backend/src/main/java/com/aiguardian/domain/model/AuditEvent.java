package com.aiguardian.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String requestId;
    private AuditEventType eventType;
    private boolean success;
    private Long durationMs;
    private String errorCode;
    private Map<String, Object> detail;
    private OffsetDateTime createdAt;
}
