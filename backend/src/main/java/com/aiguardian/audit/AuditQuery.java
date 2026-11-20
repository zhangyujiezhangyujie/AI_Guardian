package com.aiguardian.audit;

public record AuditQuery(
        int page,
        int size,
        String requestId,
        String state,
        String action,
        String mode,
        String from,
        String to) {
}
