package com.aiguardian.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.aiguardian.infrastructure.persistence.JdbcApprovalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** approval_request 的 SQL 在 H2（执行仓库 db/init.sql 的 P1 段落）上验证。 */
class JdbcApprovalRepositoryTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 23, 10, 0, 0, 0, ZoneOffset.ofHours(8));

    private JdbcTemplate jdbc;

    private JdbcApprovalRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = H2MockBusinessDatabase.create();
        repository = new JdbcApprovalRepository(jdbc, new ObjectMapper());
    }

    private ApprovalRequest pending(String id, OffsetDateTime createdAt) {
        return ApprovalRequest.builder()
                .approvalId(id)
                .toolCallId("tool_aaaaaaaaaaaa")
                .toolName("delete_record")
                .arguments(Map.of("recordId", 1001))
                .requester("admin")
                .basedOnRequestId(null)
                .status(ApprovalStatus.PENDING)
                .createdAt(createdAt.toString())
                .expiresAt(createdAt.plusMinutes(1440).toString())
                .build();
    }

    @Test
    void insertAndReadBack() {
        repository.insert(pending("apr_000000000001", T0), "tool_aaaaaaaaaaaa");
        ApprovalRequest a = repository.findById("apr_000000000001").orElseThrow();
        assertEquals(ApprovalStatus.PENDING, a.getStatus());
        assertEquals(1001, ((Number) a.getArguments().get("recordId")).intValue());
        assertEquals("admin", a.getRequester());
        assertTrue(a.getCreatedAt().startsWith("2026-11-23T10:00"));
        assertTrue(a.getExpiresAt().startsWith("2026-11-24T10:00"));
        assertNull(a.getDecidedAt());
        assertEquals("tool_aaaaaaaaaaaa",
                jdbc.queryForObject("SELECT audit_request_id FROM approval_request WHERE id = 'apr_000000000001'", String.class));
    }

    @Test
    void claimFinishAndRejectAreConditional() {
        repository.insert(pending("apr_000000000001", T0), "x");
        repository.insert(pending("apr_000000000002", T0), "x");
        OffsetDateTime now = T0.plusMinutes(5);

        assertEquals(1, repository.claim("apr_000000000001", "manager", "ok", now));
        assertEquals(0, repository.claim("apr_000000000001", "manager", "ok", now));
        assertEquals(0, repository.reject("apr_000000000001", "manager", null, now));
        assertEquals(1, repository.finish("apr_000000000001", ApprovalStatus.EXECUTED, null));
        assertEquals(0, repository.finish("apr_000000000001", ApprovalStatus.FAILED, "x"));

        assertEquals(1, repository.reject("apr_000000000002", "manager", "不同意", now));
        ApprovalRequest rejected = repository.findById("apr_000000000002").orElseThrow();
        assertEquals(ApprovalStatus.REJECTED, rejected.getStatus());
        assertEquals("不同意", rejected.getComment());
        assertTrue(rejected.getDecidedAt().startsWith("2026-11-23T10:05"));
    }

    @Test
    void expiryAndListing() {
        repository.insert(pending("apr_000000000001", T0), "x");
        repository.insert(pending("apr_000000000002", T0.plusHours(2)), "x");
        OffsetDateTime afterFirstExpires = T0.plusMinutes(1440).plusMinutes(1);

        assertEquals(0, repository.claim("apr_000000000001", "manager", null, afterFirstExpires));
        assertEquals(1, repository.expirePending(afterFirstExpires));
        assertEquals(ApprovalStatus.EXPIRED, repository.findById("apr_000000000001").orElseThrow().getStatus());

        assertEquals(java.util.List.of("apr_000000000002", "apr_000000000001"),
                repository.findAll(null, 100).stream().map(ApprovalRequest::getApprovalId).toList());
        assertEquals(1, repository.findAll(ApprovalStatus.PENDING, 100).size());
    }

    @Test
    void resetScriptClearsApprovals() {
        repository.insert(pending("apr_000000000001", T0), "x");
        H2MockBusinessDatabase.reset(jdbc);
        assertTrue(repository.findAll(null, 100).isEmpty());
    }
}
