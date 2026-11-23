package com.aiguardian.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.BusinessRecord;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.ToolDenyReason;
import com.aiguardian.domain.tool.InMemoryBusinessRecordRepository;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolCallServiceTest {

    private final AiGuardianProperties properties = new AiGuardianProperties();

    private final InMemoryBusinessRecordRepository records = new InMemoryBusinessRecordRepository();

    private final List<AuditEvent> events = new ArrayList<>();

    private final AuditService audit = new AuditService() {
        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        @Override
        public void saveRequestRecord(RequestRecord record) {
        }
    };

    private final EvidenceRetriever retriever = new EvidenceRetriever() {
        @Override
        public List<Evidence> retrieve(String query, int topK) {
            return List.of(Evidence.builder().evidenceId("doc002_chunk01").documentId("doc002")
                    .documentTitle("请假制度").section("总则与事假").content("…").score(2.0).rank(1).build());
        }

        @Override
        public Optional<Evidence> findById(String evidenceId) {
            return Optional.empty();
        }
    };

    private ToolCallService service() {
        return service(records);
    }

    private final com.aiguardian.domain.tool.InMemoryApprovalRepository approvals = new com.aiguardian.domain.tool.InMemoryApprovalRepository();

    private ToolCallService service(com.aiguardian.domain.tool.BusinessRecordRepository repository) {
        ToolCatalog catalog = new ToolCatalog();
        return new ToolCallService(new ToolGateway(properties, catalog, repository),
                new ToolExecutor(catalog, repository, retriever), approvals, requestId -> Optional.empty(), audit, properties);
    }

    private List<AuditEventType> eventTypes() {
        return events.stream().map(AuditEvent::getEventType).toList();
    }

    @Test
    void deniedDeleteWritesTwoAuditEventsAndShowsUnchangedRecord() {
        ToolCallResult result = service().execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), "user");

        assertTrue(result.getToolCallId().matches("^tool_[0-9a-f]{12}$"));
        assertEquals(ToolDecision.DENY, result.getDecision());
        assertEquals(ToolDenyReason.PERMISSION_DENIED, result.getDenyReason());
        assertEquals("当前用户无权使用该工具，已拒绝。", result.getMessage());
        assertFalse(result.isExecuted());
        assertNull(result.getResult());
        assertEquals(1001L, result.getRecord().getRecordId());
        assertFalse(result.getRecord().isDeleted());
        assertEquals(0, records.writes());

        assertEquals(List.of(AuditEventType.TOOL_CALL_REQUESTED, AuditEventType.TOOL_CALL_DECIDED), eventTypes());
        // 没有依据记录时，审计 requestId 使用 toolCallId
        events.forEach(event -> assertEquals(result.getToolCallId(), event.getRequestId()));
        assertEquals(List.of("toolCallId", "toolName", "user", "basedOnRequestId"), List.copyOf(events.get(0).getDetail().keySet()));
        assertEquals(List.of("toolCallId", "decision", "denyReason"), List.copyOf(events.get(1).getDetail().keySet()));
        assertEquals("DENY", events.get(1).getDetail().get("decision"));
        assertEquals("PERMISSION_DENIED", events.get(1).getDetail().get("denyReason"));
        assertTrue(events.get(1).isSuccess());
    }

    @Test
    void auditEventsUseBasedOnRequestIdWhenPresent() {
        service().executeWithBasis(new ToolCallRequest("delete_record", Map.of("recordId", 1001), "req_aaaaaaaaaaaa"), "user", null);
        events.forEach(event -> assertEquals("req_aaaaaaaaaaaa", event.getRequestId()));
    }

    @Test
    void allowedUpdateExecutesAndWritesExecutedEvent() {
        ToolCallResult result = service().execute(
                new ToolCallRequest("update_record", Map.of("recordId", 1002, "status", "CLOSED"), null), "manager");

        assertEquals(ToolDecision.ALLOW, result.getDecision());
        assertTrue(result.isExecuted());
        assertEquals(Map.of("recordId", 1002L, "statusBefore", "OPEN", "statusAfter", "CLOSED"), result.getResult());
        assertEquals("CLOSED", result.getRecord().getStatus());
        assertEquals(1, records.writes());
        assertEquals(List.of(AuditEventType.TOOL_CALL_REQUESTED, AuditEventType.TOOL_CALL_DECIDED,
                AuditEventType.TOOL_CALL_EXECUTED), eventTypes());
        assertEquals(1002L, events.get(2).getDetail().get("recordId"));
    }

    @Test
    void allowedSearchReturnsHitsWithoutRecord() {
        ToolCallResult result = service().execute(new ToolCallRequest("search_document", Map.of("keyword", "请假"), null), "user");
        assertEquals(ToolDecision.ALLOW, result.getDecision());
        assertNull(result.getRecord());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) result.getResult().get("hits");
        assertEquals("doc002_chunk01", hits.get(0).get("evidenceId"));
        assertNull(events.get(2).getDetail().get("recordId"));
    }

    @Test
    void approvalRequiredIsNotExecutedAndCreatesAPendingApproval() {
        ToolCallResult result = service().execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), "manager");
        assertEquals(ToolDecision.REQUIRE_APPROVAL, result.getDecision());
        assertFalse(result.isExecuted());
        assertFalse(result.getRecord().isDeleted());
        assertEquals(0, records.writes());

        assertTrue(result.getApprovalId().matches("^apr_[0-9a-f]{12}$"));
        var approval = approvals.findById(result.getApprovalId()).orElseThrow();
        assertEquals(com.aiguardian.domain.model.ApprovalStatus.PENDING, approval.getStatus());
        assertEquals("manager", approval.getRequester());
        assertEquals(result.getToolCallId(), approval.getToolCallId());
        assertEquals(java.time.Duration.ofMinutes(1440), java.time.Duration.between(
                java.time.OffsetDateTime.parse(approval.getCreatedAt()), java.time.OffsetDateTime.parse(approval.getExpiresAt())));
        assertEquals(result.getToolCallId(), approvals.auditIdOf(result.getApprovalId()));

        assertEquals(List.of(AuditEventType.TOOL_CALL_REQUESTED, AuditEventType.TOOL_CALL_DECIDED,
                AuditEventType.APPROVAL_REQUESTED), eventTypes());
        assertEquals(result.getApprovalId(), events.get(2).getDetail().get("approvalId"));
    }

    @Test
    void otherDecisionsHaveNoApprovalId() {
        assertNull(service().execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), "user").getApprovalId());
        assertNull(service().execute(new ToolCallRequest("search_document", Map.of("keyword", "年假"), null), "user").getApprovalId());
    }

    @Test
    void executionFailureIsAuditedAndRaisedAsInternalError() {
        var failing = new InMemoryBusinessRecordRepository() {
            @Override
            public int updateStatus(long recordId, String status, OffsetDateTime now) {
                throw new IllegalStateException("db down");
            }
        };
        BusinessException e = assertThrows(BusinessException.class, () -> service(failing).execute(
                new ToolCallRequest("update_record", Map.of("recordId", 1002, "status", "CLOSED"), null), "manager"));
        assertEquals(ErrorCode.INTERNAL_ERROR, e.getErrorCode());
        AuditEvent executed = events.get(2);
        assertEquals(AuditEventType.TOOL_CALL_EXECUTED, executed.getEventType());
        assertFalse(executed.isSuccess());
        assertEquals("INTERNAL_ERROR", executed.getErrorCode());
    }

    @Test
    void denialSurvivesUnavailableDatabaseForTheDisplaySnapshot() {
        var down = new InMemoryBusinessRecordRepository() {
            @Override
            public Optional<BusinessRecord> findById(long recordId) {
                throw new IllegalStateException("db down");
            }
        };
        // 权限检查在读库之前完成：数据库不可用时仍返回 DENY，record 为 null
        ToolCallResult result = service(down).execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), "user");
        assertEquals(ToolDenyReason.PERMISSION_DENIED, result.getDenyReason());
        assertNull(result.getRecord());
    }

    @Test
    void authorizedRecordToolFailsClosedWhenDatabaseIsUnavailable() {
        var down = new InMemoryBusinessRecordRepository() {
            @Override
            public Optional<BusinessRecord> findById(long recordId) {
                throw new IllegalStateException("db down");
            }
        };
        // 记录存在性无法确认时不执行，异常向上抛出（接口返回 500），不会误放行
        assertThrows(IllegalStateException.class, () -> service(down).execute(
                new ToolCallRequest("update_record", Map.of("recordId", 1002, "status", "CLOSED"), null), "manager"));
        assertEquals(0, down.writes());
    }

    @Test
    void recordSnapshotIsNullForUnparseableRecordId() {
        ToolCallResult result = service().execute(new ToolCallRequest("delete_record", Map.of("recordId", "1001"), null), "manager");
        assertEquals(ToolDenyReason.INVALID_ARGUMENTS, result.getDenyReason());
        assertNull(result.getRecord());
        BusinessRecord unchanged = records.findById(1001).orElseThrow();
        assertFalse(unchanged.isDeleted());
    }
}
