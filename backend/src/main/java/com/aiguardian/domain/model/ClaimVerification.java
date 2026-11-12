package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimVerification {
    private String claimId;
    private String claim;
    private String originalText;
    private Integer startIndex;
    private Integer endIndex;
    private boolean modelSupported;
    private ClaimStatus status;
    private String evidenceId;
    private String quote;
    private Boolean quoteExists;
    private InsufficientReason insufficientReason;
}
