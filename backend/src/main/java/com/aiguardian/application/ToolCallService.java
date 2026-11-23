package com.aiguardian.application;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.common.IdGenerator;
import com.aiguardian.common.ShanghaiTime;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.tool.ApprovalRepository;
import com.aiguardian.domain.tool.ToolBasisLookup;
import com.aiguardian.domain.tool.ToolDecisionOutcome;
import com.aiguardian.domain.tool.ToolGateway;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 工具调用的应用层入口（契约 7.10）：写审计 → 网关决策 → 仅 ALLOW 时执行 → 读取目标记录当前状态。
 * v1.4：决策为 REQUIRE_APPROVAL 时生成审批单（契约 7.11）。
 */
@Slf4j
@Service
@Profile("!stub")
public class ToolCallService {

    private final ToolGateway gateway;

    private final ToolExecutor executor;

    private final ApprovalRepository approvals;

    private final ToolBasisLookup basisLookup;

    private final AuditService audit;

    private final AiGuardianProperties properties;

    public ToolCallService(ToolGateway gateway, ToolExecutor executor, ApprovalRepository approvals,
                           ToolBasisLookup basisLookup, AuditService audit, AiGuardianProperties properties) {
        this.gateway = gateway;
        this.executor = executor;
        this.approvals = approvals;
        this.basisLookup = basisLookup;
        this.audit = audit;
        this.properties = properties;
    }

    /** POST /api/tools/execute：依据报告从审计记录中查询。 */
    public ToolCallResult execute(ToolCallRequest request, String user) {
        return run(request, user, () -> basisLookup.find(request.getBasedOnRequestId()));
    }

    /** 演示 SC06：依据报告直接使用本次问答的核验报告（契约 7.7）。 */
    public ToolCallResult executeWithBasis(ToolCallRequest request, String user, VerificationReport basis) {
        return run(request, user, () -> Optional.ofNullable(basis));
    }

    /** 审计事件的 requestId：依据的问答 requestId，为空时用 toolCallId（契约 7.10）。 */
    static String auditId(String basedOnRequestId, String toolCallId) {
        return basedOnRequestId == null ? toolCallId : basedOnRequestId;
    }

    private ToolCallResult run(ToolCallRequest request, String user,
                               Supplier<Optional<VerificationReport>> basis) {
        String toolCallId = IdGenerator.toolCallId();
        String auditId = auditId(request.getBasedOnRequestId(), toolCallId);
        OffsetDateTime createdAt = ShanghaiTime.now();

        record(audit, auditId, AuditEventType.TOOL_CALL_REQUESTED, true, null, null, detail(
                "toolCallId", toolCallId,
                "toolName", request.getToolName(),
                "user", user,
                "basedOnRequestId", request.getBasedOnRequestId()));

        long decideStart = System.nanoTime();
        ToolDecisionOutcome outcome = gateway.decide(request, user, basis);
        record(audit, auditId, AuditEventType.TOOL_CALL_DECIDED, true, elapsedMs(decideStart), null, detail(
                "toolCallId", toolCallId,
                "decision", outcome.decision().name(),
                "denyReason", outcome.denyReason() == null ? null : outcome.denyReason().name()));

        Map<String, Object> result = null;
        boolean executed = false;
        String approvalId = null;
        if (outcome.decision() == ToolDecision.ALLOW) {
            long executeStart = System.nanoTime();
            Long recordId = outcome.arguments().recordId();
            try {
                result = executor.execute(request.getToolName(), outcome.arguments());
                executed = true;
            } catch (RuntimeException e) {
                log.error("toolCallId={} toolExecutionFailed exceptionType={}", toolCallId, e.getClass().getName());
                record(audit, auditId, AuditEventType.TOOL_CALL_EXECUTED, false, elapsedMs(executeStart),
                        ErrorCode.INTERNAL_ERROR.name(), detail(
                                "toolCallId", toolCallId,
                                "toolName", request.getToolName(),
                                "recordId", recordId));
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, request.getBasedOnRequestId());
            }
            record(audit, auditId, AuditEventType.TOOL_CALL_EXECUTED, true, elapsedMs(executeStart), null, detail(
                    "toolCallId", toolCallId,
                    "toolName", request.getToolName(),
                    "recordId", recordId));
        } else if (outcome.decision() == ToolDecision.REQUIRE_APPROVAL) {
            approvalId = createApproval(request, user, toolCallId, auditId, createdAt);
        }

        return ToolCallResult.builder()
                .toolCallId(toolCallId)
                .basedOnRequestId(request.getBasedOnRequestId())
                .user(user)
                .toolName(request.getToolName())
                .arguments(request.getArguments())
                .decision(outcome.decision())
                .denyReason(outcome.denyReason())
                .message(outcome.message())
                .executed(executed)
                .result(result)
                .record(executor.currentRecord(request.getToolName(), request.getArguments()))
                .approvalId(approvalId)
                .createdAt(createdAt.toString())
                .build();
    }

    /** 契约 7.11：生成 PENDING 审批单，有效期为 approval-ttl-minutes。 */
    private String createApproval(ToolCallRequest request, String user, String toolCallId, String auditId,
                                  OffsetDateTime createdAt) {
        OffsetDateTime expiresAt = createdAt.plusMinutes(properties.getToolGateway().getApprovalTtlMinutes());
        ApprovalRequest approval = ApprovalRequest.builder()
                .approvalId(IdGenerator.approvalId())
                .toolCallId(toolCallId)
                .toolName(request.getToolName())
                .arguments(request.getArguments())
                .requester(user)
                .basedOnRequestId(request.getBasedOnRequestId())
                .status(ApprovalStatus.PENDING)
                .createdAt(createdAt.toString())
                .expiresAt(expiresAt.toString())
                .build();
        approvals.insert(approval, auditId);
        record(audit, auditId, AuditEventType.APPROVAL_REQUESTED, true, null, null, detail(
                "approvalId", approval.getApprovalId(),
                "toolCallId", toolCallId,
                "requester", user,
                "expiresAt", approval.getExpiresAt()));
        return approval.getApprovalId();
    }

    static void record(AuditService audit, String requestId, AuditEventType type, boolean success, Long durationMs,
                       String errorCode, Map<String, Object> detail) {
        audit.record(AuditEvent.builder()
                .requestId(requestId)
                .eventType(type)
                .success(success)
                .durationMs(durationMs)
                .errorCode(errorCode)
                .detail(detail)
                .createdAt(ShanghaiTime.now())
                .build());
    }

    /** 允许 null 值、保持键顺序的 Map（Map.of 不接受 null）。 */
    static Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
