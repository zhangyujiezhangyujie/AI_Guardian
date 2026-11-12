package com.aiguardian.domain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationInput {
    private String requestId;
    private RunMode mode;
    private String scenarioId;
    private String question;
    private String answer;
    private List<Evidence> evidence;
}
