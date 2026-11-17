package com.aiguardian.audit;

import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.VerificationState;
import java.time.OffsetDateTime;

public record AuditListItem(
        String requestId,
        OffsetDateTime createdAt,
        RunMode mode,
        String scenarioId,
        String questionPreview,
        VerificationState verificationState,
        String guardStrategy,
        GuardAction guardAction,
        int totalClaims,
        int supportedClaims,
        int insufficientClaims,
        long totalMs,
        String errorCode) {
}
