package com.aiguardian.demo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场景对工具网关决策的期望（契约 10.6、10.7，v1.3）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedToolCall {
    private String decision;
    private String denyReason;
}
