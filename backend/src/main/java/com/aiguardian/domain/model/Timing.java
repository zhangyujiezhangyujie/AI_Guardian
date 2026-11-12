package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Timing {
    private long retrievalMs;
    private Long generationMs;
    private Long verificationMs;
    private long totalMs;
}
