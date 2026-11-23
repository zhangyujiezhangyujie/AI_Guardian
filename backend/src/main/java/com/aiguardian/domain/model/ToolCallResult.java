package com.aiguardian.domain.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工具网关决策结果（契约 3.11、7.10，P1）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResult {
    private String toolCallId;
    private String basedOnRequestId;
    private String user;
    private String toolName;
    private Map<String, Object> arguments;
    private ToolDecision decision;
    private ToolDenyReason denyReason;
    private String message;
    private boolean executed;
    private Map<String, Object> result;
    private BusinessRecord record;
    /** v1.4：decision=REQUIRE_APPROVAL 时的审批单号。 */
    private String approvalId;
    private String createdAt;
}
