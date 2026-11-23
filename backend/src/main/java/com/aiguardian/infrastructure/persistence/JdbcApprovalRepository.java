package com.aiguardian.infrastructure.persistence;

import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.aiguardian.domain.tool.ApprovalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** approval_request 的 JDBC 实现（契约 9，P1 v1.4）。只用标准 SQL，MySQL 与测试用的 H2 都可运行。 */
@Repository
@Profile("!stub")
public class JdbcApprovalRepository implements ApprovalRepository {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {
    };

    private static final String COLUMNS = "id, tool_call_id, tool_name, arguments_json, requester, based_on_request_id, "
            + "status, approver, comment, failure_reason, created_at, expires_at, decided_at";

    private final JdbcTemplate jdbc;

    private final ObjectMapper json;

    public JdbcApprovalRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(ApprovalRequest a, String auditRequestId) {
        jdbc.update("""
                        INSERT INTO approval_request (id, tool_call_id, audit_request_id, tool_name, arguments_json,
                            requester, based_on_request_id, status, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                a.getApprovalId(),
                a.getToolCallId(),
                auditRequestId,
                a.getToolName(),
                writeJson(a.getArguments()),
                a.getRequester(),
                a.getBasedOnRequestId(),
                a.getStatus().name(),
                toTimestamp(OffsetDateTime.parse(a.getCreatedAt())),
                toTimestamp(OffsetDateTime.parse(a.getExpiresAt())));
    }

    @Override
    public Optional<ApprovalRequest> findById(String approvalId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM approval_request WHERE id = ?",
                (rs, rowNum) -> map(rs), approvalId).stream().findFirst();
    }

    @Override
    public List<ApprovalRequest> findAll(ApprovalStatus status, int limit) {
        if (status == null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM approval_request ORDER BY created_at DESC, id DESC LIMIT ?",
                    (rs, rowNum) -> map(rs), limit);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM approval_request WHERE status = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> map(rs), status.name(), limit);
    }

    @Override
    public int expirePending(OffsetDateTime now) {
        Timestamp ts = toTimestamp(now);
        return jdbc.update("UPDATE approval_request SET status = 'EXPIRED', decided_at = ? WHERE status = 'PENDING' AND expires_at < ?",
                ts, ts);
    }

    @Override
    public int claim(String approvalId, String approver, String comment, OffsetDateTime now) {
        Timestamp ts = toTimestamp(now);
        return jdbc.update("""
                        UPDATE approval_request SET status = 'APPROVED', approver = ?, comment = ?, decided_at = ?
                        WHERE id = ? AND status = 'PENDING' AND expires_at >= ?
                        """,
                approver, comment, ts, approvalId, ts);
    }

    @Override
    public int finish(String approvalId, ApprovalStatus status, String failureReason) {
        return jdbc.update("UPDATE approval_request SET status = ?, failure_reason = ? WHERE id = ? AND status = 'APPROVED'",
                status.name(), failureReason, approvalId);
    }

    @Override
    public int reject(String approvalId, String approver, String comment, OffsetDateTime now) {
        Timestamp ts = toTimestamp(now);
        return jdbc.update("""
                        UPDATE approval_request SET status = 'REJECTED', approver = ?, comment = ?, decided_at = ?
                        WHERE id = ? AND status = 'PENDING' AND expires_at >= ?
                        """,
                approver, comment, ts, approvalId, ts);
    }

    private ApprovalRequest map(ResultSet rs) throws SQLException {
        return ApprovalRequest.builder()
                .approvalId(rs.getString("id"))
                .toolCallId(rs.getString("tool_call_id"))
                .toolName(rs.getString("tool_name"))
                .arguments(readJson(rs.getString("arguments_json")))
                .requester(rs.getString("requester"))
                .basedOnRequestId(rs.getString("based_on_request_id"))
                .status(ApprovalStatus.valueOf(rs.getString("status")))
                .approver(rs.getString("approver"))
                .comment(rs.getString("comment"))
                .failureReason(rs.getString("failure_reason"))
                .createdAt(iso(rs.getTimestamp("created_at")))
                .expiresAt(iso(rs.getTimestamp("expires_at")))
                .decidedAt(iso(rs.getTimestamp("decided_at")))
                .build();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化工具参数。", e);
        }
    }

    private Map<String, Object> readJson(String value) {
        try {
            return json.readValue(value, ARGUMENTS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审批单中的工具参数无法解析。", e);
        }
    }

    private static String iso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().atZone(SHANGHAI).toOffsetDateTime().toString();
    }

    private static Timestamp toTimestamp(OffsetDateTime value) {
        return Timestamp.valueOf(value.atZoneSameInstant(SHANGHAI).toLocalDateTime());
    }
}
