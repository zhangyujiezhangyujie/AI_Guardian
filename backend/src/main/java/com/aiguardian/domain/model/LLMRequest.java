package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {
    private String requestId;
    private RunMode mode;
    private String scenarioId;
    private LLMPurpose purpose;
    private int attempt;
    private String systemPrompt;
    private String userPrompt;
    private double temperature;
    private boolean jsonMode;
    private long timeoutMs;
}
