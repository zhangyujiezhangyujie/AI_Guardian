package com.aiguardian.domain.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审批单（契约 3.12，P1 v1.4）。result、record 只在批准或驳回接口的响应中填写。 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    private String approvalId;
    private String toolCallId;
    private String toolName;
    private Map<String, Object> arguments;
    private String requester;
    private String basedOnRequestId;
    private ApprovalStatus status;
    private String approver;
    private String comment;
    private String failureReason;
    private Map<String, Object> result;
    private BusinessRecord record;
    private String createdAt;
    private String expiresAt;
    private String decidedAt;
}
