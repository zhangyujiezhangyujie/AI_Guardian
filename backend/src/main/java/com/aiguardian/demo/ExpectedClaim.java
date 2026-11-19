package com.aiguardian.demo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 期望的单条结论（契约 10.6）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedClaim {
    private String claimContains;
    private String status;
    private String insufficientReason;
}
