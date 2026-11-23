package com.aiguardian.application;

import static com.aiguardian.application.ToolCallService.auditId;
import static com.aiguardian.application.ToolCallService.detail;
import static com.aiguardian.application.ToolCallService.elapsedMs;
import static com.aiguardian.application.ToolCallService.record;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.common.ShanghaiTime;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.tool.ApprovalRepository;
import com.aiguardian.domain.tool.ToolBasisLookup;
import com.aiguardian.domain.tool.ToolDecisionOutcome;
import com.aiguardian.domain.tool.ToolGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 简化审批（契约 7.11，P1 v1.4）：批准后以申请人身份重新走一遍网关决策链（跳过审批步骤）再执行；
 * 禁止自批；过期在每次读取或处理时判断，不设定时任务。
 */
@Slf4j
@Service
@Profile("!stub")
public class ApprovalService {

    public static final String EXECUTION_FAILED = "EXECUTION_FAILED";

    private static final int LIST_LIMIT = 100;

    private final ApprovalRepository approvals;

    private final ToolGateway gateway;

    private final ToolExecutor executor;

    private final ToolBasisLookup basisLookup;

    private final AuditService audit;

    private final AiGuardianProperties properties;

    public ApprovalService(ApprovalRepository approvals, ToolGateway gateway, ToolExecutor executor,
                           ToolBasisLookup basisLookup, AuditService audit, AiGuardianProperties properties) {
        this.approvals = approvals;
        this.gateway = gateway;
        this.executor = executor;
        this.basisLookup = basisLookup;
        this.audit = audit;
        this.properties = properties;
    }

    public List<ApprovalRequest> list(ApprovalStatus status) {
        approvals.expirePending(ShanghaiTime.now());
        return approvals.findAll(status, LIST_LIMIT);
    }

    public ApprovalRequest get(String approvalId) {
        approvals.expirePending(ShanghaiTime.now());
        return load(approvalId);
    }

    public ApprovalRequest approve(String approvalId, String user, String comment) {
        OffsetDateTime now = ShanghaiTime.now();
        ApprovalRequest approval = checkActionable(approvalId, user, now);

        if (approvals.claim(approvalId, user, comment, now) != 1) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_NOT_PENDING, "审批单已被处理。");
        }
        String auditId = auditId(approval.getBasedOnRequestId(), approval.getToolCallId());
        record(audit, auditId, AuditEventType.APPROVAL_DECIDED, true, null, null, detail(
                "approvalId", approvalId,
                "status", ApprovalStatus.APPROVED.name(),
                "approver", user));

        // 重新检查：以申请人身份重走决策链，跳过审批步骤
        ToolCallRequest request = new ToolCallRequest(approval.getToolName(), approval.getArguments(),
                approval.getBasedOnRequestId());
        long decideStart = System.nanoTime();
        ToolDecisionOutcome outcome = gateway.decide(request, approval.getRequester(),
                () -> basisLookup.find(approval.getBasedOnRequestId()), true);
        record(audit, auditId, AuditEventType.TOOL_CALL_DECIDED, true, elapsedMs(decideStart), null, detail(
                "toolCallId", approval.getToolCallId(),
                "decision", outcome.decision().name(),
                "denyReason", outcome.denyReason() == null ? null : outcome.denyReason().name()));

        Map<String, Object> result = null;
        if (outcome.decision() != ToolDecision.ALLOW) {
            approvals.finish(approvalId, ApprovalStatus.FAILED, outcome.denyReason().name());
        } else {
            long executeStart = System.nanoTime();
            try {
                result = executor.execute(approval.getToolName(), outcome.arguments());
                approvals.finish(approvalId, ApprovalStatus.EXECUTED, null);
                record(audit, auditId, AuditEventType.TOOL_CALL_EXECUTED, true, elapsedMs(executeStart), null, detail(
                        "toolCallId", approval.getToolCallId(),
                        "toolName", approval.getToolName(),
                        "recordId", outcome.arguments().recordId()));
            } catch (RuntimeException e) {
                log.error("approvalId={} approvedExecutionFailed exceptionType={}", approvalId, e.getClass().getName());
                approvals.finish(approvalId, ApprovalStatus.FAILED, EXECUTION_FAILED);
                record(audit, auditId, AuditEventType.TOOL_CALL_EXECUTED, false, elapsedMs(executeStart),
                        ErrorCode.INTERNAL_ERROR.name(), detail(
                                "toolCallId", approval.getToolCallId(),
                                "toolName", approval.getToolName(),
                                "recordId", outcome.arguments().recordId()));
            }
        }
        return withSnapshot(load(approvalId), result);
    }

    public ApprovalRequest reject(String approvalId, String user, String comment) {
        OffsetDateTime now = ShanghaiTime.now();
        ApprovalRequest approval = checkActionable(approvalId, user, now);
        if (approvals.reject(approvalId, user, comment, now) != 1) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_NOT_PENDING, "审批单已被处理。");
        }
        record(audit, auditId(approval.getBasedOnRequestId(), approval.getToolCallId()),
                AuditEventType.APPROVAL_DECIDED, true, null, null, detail(
                        "approvalId", approvalId,
                        "status", ApprovalStatus.REJECTED.name(),
                        "approver", user));
        return withSnapshot(load(approvalId), null);
    }

    /** 契约 7.11 规则 1–5：存在、未过期、待审批、有审批权限、不是申请人本人。 */
    private ApprovalRequest checkActionable(String approvalId, String user, OffsetDateTime now) {
        approvals.expirePending(now);
        ApprovalRequest approval = load(approvalId);
        if (approval.getStatus() == ApprovalStatus.EXPIRED) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_NOT_PENDING, "审批单已过期。");
        }
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_NOT_PENDING,
                    "当前状态为 " + approval.getStatus().name() + "。");
        }
        if (!properties.getToolGateway().getApprovers().contains(user)) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_FORBIDDEN, "演示用户 " + user + " 没有审批权限。");
        }
        if (user.equals(approval.getRequester())) {
            throw BusinessException.withReason(ErrorCode.APPROVAL_FORBIDDEN, "申请人不能审批自己的申请。");
        }
        return approval;
    }

    private ApprovalRequest load(String approvalId) {
        return approvals.findById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
    }

    private ApprovalRequest withSnapshot(ApprovalRequest approval, Map<String, Object> result) {
        return approval.toBuilder()
                .result(result)
                .record(executor.currentRecord(approval.getToolName(), approval.getArguments()))
                .build();
    }
}
