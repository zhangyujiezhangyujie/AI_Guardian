package com.aiguardian.demo;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场景的期望结果（契约 10.4、10.6）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioExpected {
    private int httpStatus;
    private String errorCode;
    private List<String> evidenceIdsContain;
    private String verificationState;
    private String failureReason;
    private List<ExpectedClaim> claims;
    private Map<String, String> guardByStrategy;
    /** v1.3，可空：对工具网关决策的期望（契约 10.7）。 */
    private ExpectedToolCall toolCall;
}
