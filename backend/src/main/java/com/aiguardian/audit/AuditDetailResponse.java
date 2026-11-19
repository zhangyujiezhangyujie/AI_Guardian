package com.aiguardian.audit;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.Timing;
import com.aiguardian.domain.model.VerificationReport;
import java.time.OffsetDateTime;
import java.util.List;

public record AuditDetailResponse(
        String requestId,
        String sessionId,
        OffsetDateTime createdAt,
        RunMode mode,
        String scenarioId,
        String question,
        String rawAnswer,
        List<Evidence> evidence,
        VerificationReport verification,
        AuditGuardSnapshot guard,
        Timing timing,
        String errorCode,
        List<AuditEvent> events) {
}
