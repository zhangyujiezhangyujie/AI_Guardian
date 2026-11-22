package com.aiguardian.application;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.common.IdGenerator;
import com.aiguardian.common.ShanghaiTime;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.BusinessRecord;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.tool.BusinessRecordRepository;
import com.aiguardian.domain.tool.ToolArguments;
import com.aiguardian.domain.tool.ToolBasisLookup;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolDecisionOutcome;
import com.aiguardian.domain.tool.ToolGateway;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 工具调用的应用层入口（契约 7.10）：写审计 → 网关决策 → 仅 ALLOW 时执行 → 读取目标记录当前状态。
 */
@Slf4j
@Service
@Profile("!stub")
public class ToolCallService {

    private static final int SEARCH_TOP_K = 3;

    private final ToolGateway gateway;

    private final ToolCatalog catalog;

    private final BusinessRecordRepository records;

    private final EvidenceRetriever retriever;

    private final ToolBasisLookup basisLookup;

    private final AuditService audit;

    public ToolCallService(ToolGateway gateway, ToolCatalog catalog, BusinessRecordRepository records,
                           EvidenceRetriever retriever, ToolBasisLookup basisLookup, AuditService audit) {
        this.gateway = gateway;
        this.catalog = catalog;
        this.records = records;
        this.retriever = retriever;
        this.basisLookup = basisLookup;
        this.audit = audit;
    }

    /** POST /api/tools/execute：依据报告从审计记录中查询。 */
    public ToolCallResult execute(ToolCallRequest request, String user) {
        return run(request, user, () -> basisLookup.find(request.getBasedOnRequestId()));
    }

    /** 演示 SC06：依据报告直接使用本次问答的核验报告（契约 7.7）。 */
    public ToolCallResult executeWithBasis(ToolCallRequest request, String user, VerificationReport basis) {
        return run(request, user, () -> Optional.ofNullable(basis));
    }

    private ToolCallResult run(ToolCallRequest request, String user,
                               Supplier<Optional<VerificationReport>> basis) {
        String toolCallId = IdGenerator.toolCallId();
        String auditId = request.getBasedOnRequestId() == null ? toolCallId : request.getBasedOnRequestId();
        OffsetDateTime createdAt = ShanghaiTime.now();

        record(auditId, AuditEventType.TOOL_CALL_REQUESTED, true, null, null, detail(
                "toolCallId", toolCallId,
                "toolName", request.getToolName(),
                "user", user,
                "basedOnRequestId", request.getBasedOnRequestId()));

        long decideStart = System.nanoTime();
        ToolDecisionOutcome outcome = gateway.decide(request, user, basis);
        record(auditId, AuditEventType.TOOL_CALL_DECIDED, true, elapsedMs(decideStart), null, detail(
                "toolCallId", toolCallId,
                "decision", outcome.decision().name(),
                "denyReason", outcome.denyReason() == null ? null : outcome.denyReason().name()));

        Map<String, Object> result = null;
        boolean executed = false;
        if (outcome.decision() == ToolDecision.ALLOW) {
            long executeStart = System.nanoTime();
            Long recordId = outcome.arguments().recordId();
            try {
                result = executeTool(request.getToolName(), outcome.arguments());
                executed = true;
            } catch (RuntimeException e) {
                log.error("toolCallId={} toolExecutionFailed exceptionType={}", toolCallId, e.getClass().getName());
                record(auditId, AuditEventType.TOOL_CALL_EXECUTED, false, elapsedMs(executeStart),
                        ErrorCode.INTERNAL_ERROR.name(), detail(
                                "toolCallId", toolCallId,
                                "toolName", request.getToolName(),
                                "recordId", recordId));
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, request.getBasedOnRequestId());
            }
            record(auditId, AuditEventType.TOOL_CALL_EXECUTED, true, elapsedMs(executeStart), null, detail(
                    "toolCallId", toolCallId,
                    "toolName", request.getToolName(),
                    "recordId", recordId));
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
                .record(currentRecord(request))
                .createdAt(createdAt.toString())
                .build();
    }

    private Map<String, Object> executeTool(String toolName, ToolArguments arguments) {
        OffsetDateTime now = ShanghaiTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        switch (toolName) {
            case ToolCatalog.SEARCH_DOCUMENT -> {
                List<Map<String, Object>> hits = new ArrayList<>();
                for (Evidence evidence : retriever.retrieve(arguments.keyword(), SEARCH_TOP_K)) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("evidenceId", evidence.getEvidenceId());
                    hit.put("documentTitle", evidence.getDocumentTitle());
                    hit.put("section", evidence.getSection());
                    hits.add(hit);
                }
                result.put("hits", hits);
            }
            case ToolCatalog.UPDATE_RECORD -> {
                String before = records.findById(arguments.recordId())
                        .map(BusinessRecord::getStatus)
                        .orElse(null);
                requireOneRow(records.updateStatus(arguments.recordId(), arguments.status(), now));
                result.put("recordId", arguments.recordId());
                result.put("statusBefore", before);
                result.put("statusAfter", arguments.status());
            }
            case ToolCatalog.DELETE_RECORD -> {
                requireOneRow(records.softDelete(arguments.recordId(), now));
                result.put("recordId", arguments.recordId());
            }
            default -> throw new IllegalStateException("未知工具：" + toolName);
        }
        return result;
    }

    /**
     * 记录类工具：决策之后读取目标记录，展示数据是否被修改（契约 3.11 record）。
     * 这只是展示信息：读取失败（如数据库不可用）时返回 null 并记录日志，不影响已经做出的决策。
     */
    private BusinessRecord currentRecord(ToolCallRequest request) {
        if (!catalog.isRecordTool(request.getToolName()) || request.getArguments() == null) {
            return null;
        }
        Object rawId = request.getArguments().get("recordId");
        if (!(rawId instanceof Integer) && !(rawId instanceof Long)) {
            return null;
        }
        try {
            return records.findById(((Number) rawId).longValue()).orElse(null);
        } catch (RuntimeException e) {
            log.error("recordId={} recordSnapshotFailed exceptionType={}", rawId, e.getClass().getName());
            return null;
        }
    }

    private static void requireOneRow(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("预期修改 1 行，实际 " + rows + " 行");
        }
    }

    private void record(String requestId, AuditEventType type, boolean success, Long durationMs,
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
    private static Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
