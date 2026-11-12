package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuardResult {
    private String strategy;
    private double blockThreshold;
    private GuardAction action;
    private boolean answerVisible;
    private String displayAnswer;
    private String message;
    private Double unsupportedRatio;
}
