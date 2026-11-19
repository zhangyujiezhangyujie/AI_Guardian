package com.aiguardian.controller;

import com.aiguardian.audit.AuditDetailResponse;
import com.aiguardian.audit.AuditListResponse;
import com.aiguardian.audit.AuditQuery;
import com.aiguardian.audit.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Profile("!stub")
public class AuditController {
    private final AuditQueryService auditQueryService;

    @GetMapping
    public AuditListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return auditQueryService.list(new AuditQuery(page, size, requestId, state, action, mode, from, to));
    }

    @GetMapping("/{requestId}")
    public AuditDetailResponse detail(@PathVariable String requestId) {
        return auditQueryService.findByRequestId(requestId);
    }
}
