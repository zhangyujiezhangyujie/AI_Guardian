package com.aiguardian.audit;

import java.util.List;

public record AuditListResponse(List<AuditListItem> items, long total, int page, int size) {
}
