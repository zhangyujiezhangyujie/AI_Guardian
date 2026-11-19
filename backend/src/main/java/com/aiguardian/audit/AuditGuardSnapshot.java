package com.aiguardian.audit;

import com.aiguardian.domain.model.GuardAction;

public record AuditGuardSnapshot(
        String strategy,
        Double blockThreshold,
        GuardAction action,
        Boolean answerVisible,
        String displayAnswer,
        String message,
        Double unsupportedRatio) {
}
