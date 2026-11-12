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
public class VerificationReport {
    private String requestId;
    private VerificationState verificationState;
    private VerificationFailureReason failureReason;
    private String answer;
    private int totalClaims;
    private int supportedClaims;
    private int insufficientClaims;
    private Double unsupportedRatio;
    private boolean truncated;
    @Builder.Default
    private List<ClaimVerification> claims = new ArrayList<>();
    private String verifierName;
    private int llmCalls;

    public void recalculate() {
        totalClaims = claims.size();
        supportedClaims = (int) claims.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.SUPPORTED)
                .count();
        insufficientClaims = totalClaims - supportedClaims;
        unsupportedRatio = totalClaims == 0
                ? null
                : Math.round(10000.0 * insufficientClaims / totalClaims) / 10000.0;
    }

    public static VerificationReport noEvidence(String requestId) {
        return builder()
                .requestId(requestId)
                .verificationState(VerificationState.NO_EVIDENCE)
                .verifierName("none")
                .build();
    }
}
