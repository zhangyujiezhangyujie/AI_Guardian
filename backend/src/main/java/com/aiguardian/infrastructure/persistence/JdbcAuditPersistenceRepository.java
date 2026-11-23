package com.aiguardian.infrastructure.persistence;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.VerificationReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!stub")
@RequiredArgsConstructor
public class JdbcAuditPersistenceRepository {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    // 契约 8.2：只持久化约定的 detail 键（v1.3 增加三个工具调用事件，超过 Map.of 的 10 项上限，改用 ofEntries）
    private static final Map<AuditEventType, Set<String>> EVENT_DETAIL_KEYS = Map.ofEntries(
            Map.entry(AuditEventType.REQUEST_RECEIVED, Set.of("mode", "scenarioId", "questionLength")),
            Map.entry(AuditEventType.RETRIEVAL_DONE, Set.of("topK", "hitCount", "evidenceIds", "topScore")),
            Map.entry(AuditEventType.GENERATION_DONE, Set.of("answerLength")),
            Map.entry(AuditEventType.GENERATION_FAILED, Set.of("llmErrorType")),
            Map.entry(AuditEventType.VERIFICATION_DONE,
                    Set.of("verifierName", "state", "failureReason", "totalClaims", "llmCalls")),
            Map.entry(AuditEventType.CITATION_CHECKED, Set.of("checkedCount", "passedCount", "failedReasons")),
            Map.entry(AuditEventType.GUARD_APPLIED,
                    Set.of("strategy", "blockThreshold", "action", "unsupportedRatio", "overridden")),
            Map.entry(AuditEventType.REQUEST_COMPLETED, Set.of("totalMs", "action")),
            Map.entry(AuditEventType.REQUEST_FAILED, Set.of("errorCode", "stage")),
            Map.entry(AuditEventType.TOOL_CALL_REQUESTED,
                    Set.of("toolCallId", "toolName", "user", "basedOnRequestId")),
            Map.entry(AuditEventType.TOOL_CALL_DECIDED, Set.of("toolCallId", "decision", "denyReason")),
            Map.entry(AuditEventType.TOOL_CALL_EXECUTED, Set.of("toolCallId", "toolName", "recordId")),
            Map.entry(AuditEventType.APPROVAL_REQUESTED, Set.of("approvalId", "toolCallId", "requester", "expiresAt")),
            Map.entry(AuditEventType.APPROVAL_DECIDED, Set.of("approvalId", "status", "approver")));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void insertEvent(AuditEvent event) {
        String detailJson = event.getDetail() == null ? null : writeJson(safeDetail(event));
        jdbc.update("""
                        INSERT INTO audit_event
                            (request_id, event_type, success, duration_ms, error_code, detail_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                event.getRequestId(), event.getEventType().name(), event.isSuccess(),
                event.getDurationMs() == null ? null : toSqlInt(event.getDurationMs()),
                event.getErrorCode(), detailJson, toTimestamp(event.getCreatedAt()));
    }

    @Transactional
    public void insertRequestRecord(RequestRecord record) {
        Timestamp createdAt = toTimestamp(record.getCreatedAt());
        jdbc.update("INSERT IGNORE INTO chat_session (id, created_at) VALUES (?, ?)",
                record.getSessionId(), createdAt);

        jdbc.update("""
                        INSERT INTO chat_message (session_id, request_id, role, content, created_at)
                        VALUES (?, ?, 'USER', ?, ?)
                        """,
                record.getSessionId(), record.getRequestId(), record.getQuestion(), createdAt);
        if (record.getRawAnswer() != null) {
            jdbc.update("""
                            INSERT INTO chat_message (session_id, request_id, role, content, created_at)
                            VALUES (?, ?, 'ASSISTANT', ?, ?)
                            """,
                    record.getSessionId(), record.getRequestId(), record.getRawAnswer(), createdAt);
        }

        VerificationReport report = record.getReport();
        GuardResult guard = record.getGuard();
        List<Evidence> evidence = record.getEvidence() == null ? List.of() : record.getEvidence();
        var timing = record.getTiming();
        jdbc.update("""
                        INSERT INTO verification_record (
                            request_id, session_id, mode, scenario_id, question, raw_answer,
                            verification_state, failure_reason, verifier_name, llm_calls,
                            total_claims, supported_claims, insufficient_claims, unsupported_ratio,
                            truncated, guard_strategy, guard_threshold, guard_action, evidence_json,
                            retrieval_ms, generation_ms, verification_ms, total_ms, error_code, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getRequestId(), record.getSessionId(), record.getMode().name(), record.getScenarioId(),
                record.getQuestion(), record.getRawAnswer(),
                report == null || report.getVerificationState() == null
                        ? null : report.getVerificationState().name(),
                report == null || report.getFailureReason() == null ? null : report.getFailureReason().name(),
                report == null ? null : report.getVerifierName(), report == null ? 0 : report.getLlmCalls(),
                report == null ? 0 : report.getTotalClaims(),
                report == null ? 0 : report.getSupportedClaims(),
                report == null ? 0 : report.getInsufficientClaims(),
                report == null ? null : report.getUnsupportedRatio(),
                report != null && report.isTruncated(),
                guard == null ? null : guard.getStrategy(),
                guard == null ? null : guard.getBlockThreshold(),
                guard == null || guard.getAction() == null ? null : guard.getAction().name(),
                writeJson(evidence),
                timing == null ? 0 : toSqlInt(timing.getRetrievalMs()),
                timing == null || timing.getGenerationMs() == null
                        ? null : toSqlInt(timing.getGenerationMs()),
                timing == null || timing.getVerificationMs() == null
                        ? null : toSqlInt(timing.getVerificationMs()),
                timing == null ? 0 : toSqlInt(timing.getTotalMs()),
                record.getErrorCode(), createdAt);

        if (report != null && report.getClaims() != null) {
            for (ClaimVerification claim : report.getClaims()) {
                jdbc.update("""
                                INSERT INTO claim_record (
                                    request_id, claim_id, claim, original_text, start_index, end_index,
                                    model_supported, status, evidence_id, quote, quote_exists, insufficient_reason
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        record.getRequestId(), claim.getClaimId(), safeText(claim.getClaim()),
                        claim.getOriginalText(), claim.getStartIndex(), claim.getEndIndex(),
                        claim.isModelSupported(), claim.getStatus() == null ? "INSUFFICIENT" : claim.getStatus().name(),
                        claim.getEvidenceId(), claim.getQuote(), claim.getQuoteExists(),
                        claim.getInsufficientReason() == null ? null : claim.getInsufficientReason().name());
            }
        }
    }

    private Map<String, Object> safeDetail(AuditEvent event) {
        Set<String> allowed = EVENT_DETAIL_KEYS.get(event.getEventType());
        Map<String, Object> safe = new LinkedHashMap<>();
        if (allowed != null) {
            event.getDetail().forEach((key, value) -> {
                if (allowed.contains(key)) {
                    safe.put(key, value);
                }
            });
        }
        return safe;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化审计数据。", e);
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    static Timestamp toTimestamp(OffsetDateTime value) {
        return Timestamp.valueOf(value.atZoneSameInstant(SHANGHAI).toLocalDateTime());
    }

    private static int toSqlInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
