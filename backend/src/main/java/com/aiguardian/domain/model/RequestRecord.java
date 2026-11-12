package com.aiguardian.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestRecord {
    private String requestId;
    private String sessionId;
    private RunMode mode;
    private String scenarioId;
    private String question;
    private String rawAnswer;
    @Builder.Default
    private List<Evidence> evidence = new ArrayList<>();
    private VerificationReport report;
    private GuardResult guard;
    private Timing timing;
    private String errorCode;
    private OffsetDateTime createdAt;
}
