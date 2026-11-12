package com.aiguardian.domain.model;

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
public class ChatResponse {
    private String requestId;
    private String sessionId;
    private RunMode mode;
    private String scenarioId;
    private String question;
    @Builder.Default
    private List<Evidence> evidence = new ArrayList<>();
    private VerificationReport verification;
    private GuardResult guard;
    private Timing timing;
    private String createdAt;
}
