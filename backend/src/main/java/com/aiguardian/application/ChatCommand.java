package com.aiguardian.application;

import com.aiguardian.domain.model.RunMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCommand {
    private String sessionId;
    private String question;
    private RunMode mode;
    private String scenarioId;
    private String guardStrategyOverride;
    private Double blockThresholdOverride;
    private String verifierOverride;
}
