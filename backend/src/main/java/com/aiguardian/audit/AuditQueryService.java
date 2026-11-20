package com.aiguardian.audit;

public interface AuditQueryService {
    AuditListResponse list(AuditQuery query);

    AuditDetailResponse findByRequestId(String requestId);
}
