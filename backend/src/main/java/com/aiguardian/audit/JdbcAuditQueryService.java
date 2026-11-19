package com.aiguardian.audit;

import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.InsufficientReason;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.Timing;
import com.aiguardian.domain.model.VerificationFailureReason;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Profile("!stub")
@RequiredArgsConstructor
public class JdbcAuditQueryService implements AuditQueryService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<Evidence>> EVIDENCE_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> DETAIL_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public AuditListResponse list(AuditQuery query) {
        validatePage(query.page(), query.size());
        String state = enumFilter(query.state(), VerificationState.class, "state");
        String action = enumFilter(query.action(), GuardAction.class, "action");
        String mode = enumFilter(query.mode(), RunMode.class, "mode");
        Timestamp from = parseTime(query.from(), "from");
        Timestamp to = parseTime(query.to(), "to");

        StringBuilder where = new StringBuilder(" FROM verification_record WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        addFilter(where, parameters, "request_id", query.requestId());
        addFilter(where, parameters, "verification_state", state);
        addFilter(where, parameters, "guard_action", action);
        addFilter(where, parameters, "mode", mode);
        if (from != null) {
            where.append(" AND created_at >= ?");
            parameters.add(from);
        }
        if (to != null) {
            where.append(" AND created_at < ?");
            parameters.add(to);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.size());
        pageParameters.add((long) (query.page() - 1) * query.size());
        List<AuditListItem> items = jdbc.query("""
                        SELECT request_id, created_at, mode, scenario_id, question, verification_state,
                               guard_strategy, guard_action, total_claims, supported_claims,
                               insufficient_claims, total_ms, error_code
                        """ + where + " ORDER BY created_at DESC, request_id DESC LIMIT ? OFFSET ?",
                listItemMapper(), pageParameters.toArray());
        return new AuditListResponse(items, total == null ? 0 : total, query.page(), query.size());
    }

    @Override
    public AuditDetailResponse findByRequestId(String requestId) {
        List<AuditDetailRow> rows = jdbc.query("""
                SELECT request_id, session_id, created_at, mode, scenario_id, question, raw_answer,
                       verification_state, failure_reason, verifier_name, llm_calls, total_claims,
                       supported_claims, insufficient_claims, unsupported_ratio, truncated,
                       guard_strategy, guard_threshold, guard_action, evidence_json,
                       retrieval_ms, generation_ms, verification_ms, total_ms, error_code
                FROM verification_record WHERE request_id = ?
                """, (rs, rowNum) -> mapDetailRow(rs), requestId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.AUDIT_NOT_FOUND);
        }
        AuditDetailRow row = rows.get(0);
        List<ClaimVerification> claims = jdbc.query("""
                        SELECT claim_id, claim, original_text, start_index, end_index, model_supported,
                               status, evidence_id, quote, quote_exists, insufficient_reason
                        FROM claim_record WHERE request_id = ? ORDER BY id ASC
                        """,
                (rs, rowNum) -> mapClaim(rs), requestId);
        List<AuditEvent> events = jdbc.query("""
                        SELECT event_type, success, duration_ms, error_code, detail_json, created_at
                        FROM audit_event WHERE request_id = ? ORDER BY created_at ASC, id ASC
                        """,
                (rs, rowNum) -> mapEvent(rs, requestId), requestId);

        VerificationReport verification = row.verificationState == null ? null : VerificationReport.builder()
                .requestId(requestId)
                .verificationState(VerificationState.valueOf(row.verificationState))
                .failureReason(row.failureReason == null ? null
                        : VerificationFailureReason.valueOf(row.failureReason))
                .answer(row.rawAnswer)
                .totalClaims(row.totalClaims)
                .supportedClaims(row.supportedClaims)
                .insufficientClaims(row.insufficientClaims)
                .unsupportedRatio(row.unsupportedRatio)
                .truncated(row.truncated)
                .claims(claims)
                .verifierName(row.verifierName)
                .llmCalls(row.llmCalls)
                .build();

        AuditGuardSnapshot guard = row.guardStrategy == null ? null : new AuditGuardSnapshot(
                row.guardStrategy,
                row.guardThreshold,
                row.guardAction == null ? null : GuardAction.valueOf(row.guardAction),
                null, null, null, row.unsupportedRatio);
        Timing timing = new Timing(row.retrievalMs, row.generationMs, row.verificationMs, row.totalMs);
        return new AuditDetailResponse(requestId, row.sessionId, row.createdAt, RunMode.valueOf(row.mode),
                row.scenarioId, row.question, row.rawAnswer, readEvidence(row.evidenceJson), verification,
                guard, timing, row.errorCode, events);
    }

    private RowMapper<AuditListItem> listItemMapper() {
        return (rs, rowNum) -> new AuditListItem(
                rs.getString("request_id"),
                offsetDateTime(rs.getTimestamp("created_at")),
                RunMode.valueOf(rs.getString("mode")),
                rs.getString("scenario_id"),
                preview(rs.getString("question")),
                enumValue(rs.getString("verification_state"), VerificationState.class),
                rs.getString("guard_strategy"),
                enumValue(rs.getString("guard_action"), GuardAction.class),
                rs.getInt("total_claims"),
                rs.getInt("supported_claims"),
                rs.getInt("insufficient_claims"),
                rs.getLong("total_ms"),
                rs.getString("error_code"));
    }

    private AuditDetailRow mapDetailRow(ResultSet rs) throws SQLException {
        return new AuditDetailRow(
                rs.getString("session_id"), offsetDateTime(rs.getTimestamp("created_at")),
                rs.getString("mode"), rs.getString("scenario_id"), rs.getString("question"),
                rs.getString("raw_answer"), rs.getString("verification_state"), rs.getString("failure_reason"),
                rs.getString("verifier_name"), rs.getInt("llm_calls"), rs.getInt("total_claims"),
                rs.getInt("supported_claims"), rs.getInt("insufficient_claims"),
                nullableDouble(rs, "unsupported_ratio"), rs.getBoolean("truncated"),
                rs.getString("guard_strategy"), nullableDouble(rs, "guard_threshold"),
                rs.getString("guard_action"), rs.getString("evidence_json"), rs.getLong("retrieval_ms"),
                nullableLong(rs, "generation_ms"), nullableLong(rs, "verification_ms"),
                rs.getLong("total_ms"), rs.getString("error_code"));
    }

    private ClaimVerification mapClaim(ResultSet rs) throws SQLException {
        return ClaimVerification.builder()
                .claimId(rs.getString("claim_id"))
                .claim(rs.getString("claim"))
                .originalText(rs.getString("original_text"))
                .startIndex(nullableInt(rs, "start_index"))
                .endIndex(nullableInt(rs, "end_index"))
                .modelSupported(rs.getBoolean("model_supported"))
                .status(enumValue(rs.getString("status"), ClaimStatus.class))
                .evidenceId(rs.getString("evidence_id"))
                .quote(rs.getString("quote"))
                .quoteExists(nullableBoolean(rs, "quote_exists"))
                .insufficientReason(enumValue(rs.getString("insufficient_reason"), InsufficientReason.class))
                .build();
    }

    private AuditEvent mapEvent(ResultSet rs, String requestId) throws SQLException {
        String json = rs.getString("detail_json");
        return AuditEvent.builder()
                .requestId(requestId)
                .eventType(AuditEventType.valueOf(rs.getString("event_type")))
                .success(rs.getBoolean("success"))
                .durationMs(nullableLong(rs, "duration_ms"))
                .errorCode(rs.getString("error_code"))
                .detail(json == null ? null : readJson(json, DETAIL_MAP))
                .createdAt(offsetDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private List<Evidence> readEvidence(String json) {
        return readJson(json, EVIDENCE_LIST);
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("审计记录中的 JSON 数据无法解析。", e);
        }
    }

    private static void addFilter(StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(value.strip());
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw BusinessException.invalidRequest("page 必须大于或等于 1。");
        }
        if (size < 1 || size > 100) {
            throw BusinessException.invalidRequest("size 必须在 1 到 100 之间。");
        }
    }

    private static String enumFilter(String value, Class<? extends Enum<?>> enumType, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> parsed = Enum.valueOf((Class) enumType, value.strip());
            return parsed.name();
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidRequest(name + " 参数无效。");
        }
    }

    private static Timestamp parseTime(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(OffsetDateTime.parse(value.strip())
                    .atZoneSameInstant(SHANGHAI).toLocalDateTime());
        } catch (DateTimeParseException e) {
            throw BusinessException.invalidRequest(name + " 必须是带时区的 ISO-8601 时间。");
        }
    }

    private static OffsetDateTime offsetDateTime(Timestamp timestamp) {
        return timestamp.toLocalDateTime().atZone(SHANGHAI).toOffsetDateTime();
    }

    private static String preview(String question) {
        if (question == null || question.length() <= 50) {
            return question;
        }
        return question.substring(0, 50);
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value != 0;
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record AuditDetailRow(
            String sessionId,
            OffsetDateTime createdAt,
            String mode,
            String scenarioId,
            String question,
            String rawAnswer,
            String verificationState,
            String failureReason,
            String verifierName,
            int llmCalls,
            int totalClaims,
            int supportedClaims,
            int insufficientClaims,
            Double unsupportedRatio,
            boolean truncated,
            String guardStrategy,
            Double guardThreshold,
            String guardAction,
            String evidenceJson,
            long retrievalMs,
            Long generationMs,
            Long verificationMs,
            long totalMs,
            String errorCode) {
    }
}
