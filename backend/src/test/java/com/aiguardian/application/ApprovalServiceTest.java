package com.aiguardian.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.tool.InMemoryApprovalRepository;
import com.aiguardian.domain.tool.InMemoryBusinessRecordRepository;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 契约 7.11：审批单、批准后重新检查再执行、禁止自批、过期、并发只处理一次。 */
class ApprovalServiceTest {

    private final AiGuardianProperties properties = new AiGuardianProperties();

    private final InMemoryBusinessRecordRepository records = new InMemoryBusinessRecordRepository();

    private final InMemoryApprovalRepository approvals = new InMemoryApprovalRepository();

    private final List<AuditEvent> events = new ArrayList<>();

    private final Map<String, VerificationReport> bases = new HashMap<>();

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
            return List.of();
        }

        @Override
        public Optional<Evidence> findById(String evidenceId) {
            return Optional.empty();
        }
    };

    private final ToolCatalog catalog = new ToolCatalog();

    private final ToolGateway gateway = new ToolGateway(properties, catalog, records);

    private final ToolExecutor executor = new ToolExecutor(catalog, records, retriever);

    private final ToolCallService tools = new ToolCallService(gateway, executor, approvals,
            requestId -> Optional.ofNullable(bases.get(requestId)), audit, properties);

    private final ApprovalService service = new ApprovalService(approvals, gateway, executor,
            requestId -> Optional.ofNullable(bases.get(requestId)), audit, properties);

    /** admin 申请删除记录，得到待审批单（知识库：审批通过后由系统管理员执行）。 */
    private String requestDelete(long recordId, String requester, String basedOn) {
        ToolCallResult result = tools.execute(new ToolCallRequest("delete_record", Map.of("recordId", recordId), basedOn), requester);
        assertNotNull(result.getApprovalId());
        return result.getApprovalId();
    }

    private static VerificationReport report(ClaimStatus... statuses) {
        List<ClaimVerification> claims = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            claims.add(ClaimVerification.builder().claimId("c" + (i + 1)).claim("x").status(statuses[i]).build());
        }
        VerificationReport r = VerificationReport.builder().verificationState(VerificationState.COMPLETED).claims(claims).build();
        r.recalculate();
        return r;
    }

    private BusinessException fails(Runnable action) {
        return assertThrows(BusinessException.class, action::run);
    }

    @Test
    void managerApprovesAdminDeleteAndItIsExecutedAfterRecheck() {
        String id = requestDelete(1001, "admin", null);
        events.clear();

        ApprovalRequest approved = service.approve(id, "manager", "核实无误");

        assertEquals(ApprovalStatus.EXECUTED, approved.getStatus());
        assertEquals("manager", approved.getApprover());
        assertEquals("核实无误", approved.getComment());
        assertNull(approved.getFailureReason());
        assertNotNull(approved.getDecidedAt());
        assertEquals(Map.of("recordId", 1001L), approved.getResult());
        assertTrue(approved.getRecord().isDeleted());
        assertTrue(records.findById(1001).orElseThrow().isDeleted());

        assertEquals(List.of(AuditEventType.APPROVAL_DECIDED, AuditEventType.TOOL_CALL_DECIDED, AuditEventType.TOOL_CALL_EXECUTED),
                events.stream().map(AuditEvent::getEventType).toList());
        assertEquals("APPROVED", events.get(0).getDetail().get("status"));
        assertEquals("ALLOW", events.get(1).getDetail().get("decision"));
        // 审批事件写在原工具调用的审计 requestId 下
        assertEquals(approvals.auditIdOf(id), events.get(0).getRequestId());
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        String id = requestDelete(1001, "manager", null);
        BusinessException e = fails(() -> service.approve(id, "manager", null));
        assertEquals(ErrorCode.APPROVAL_FORBIDDEN, e.getErrorCode());
        assertEquals("无权处理该审批单：申请人不能审批自己的申请。", e.getMessage());
        assertEquals(ApprovalStatus.PENDING, approvals.findById(id).orElseThrow().getStatus());
        assertFalse(records.findById(1001).orElseThrow().isDeleted());
    }

    @Test
    void onlyConfiguredApproversCanDecide() {
        String id = requestDelete(1001, "admin", null);
        for (String user : List.of("user", "admin")) {
            BusinessException e = fails(() -> service.approve(id, user, null));
            assertEquals(ErrorCode.APPROVAL_FORBIDDEN, e.getErrorCode());
            assertTrue(e.getMessage().contains("没有审批权限"));
        }
        assertEquals(ErrorCode.APPROVAL_FORBIDDEN, fails(() -> service.reject(id, "user", null)).getErrorCode());
        assertEquals(0, records.writes());
    }

    @Test
    void rejectDoesNotExecute() {
        String id = requestDelete(1001, "admin", null);
        ApprovalRequest rejected = service.reject(id, "manager", "该记录仍在使用");
        assertEquals(ApprovalStatus.REJECTED, rejected.getStatus());
        assertEquals("该记录仍在使用", rejected.getComment());
        assertNull(rejected.getResult());
        assertFalse(rejected.getRecord().isDeleted());
        assertEquals(0, records.writes());
    }

    @Test
    void recheckFailsWhenRecordWasDeletedMeanwhile() {
        String first = requestDelete(1001, "admin", null);
        String second = requestDelete(1001, "admin", null);
        service.approve(first, "manager", null);

        ApprovalRequest result = service.approve(second, "manager", null);
        assertEquals(ApprovalStatus.FAILED, result.getStatus());
        assertEquals("RECORD_NOT_FOUND", result.getFailureReason());
        assertNull(result.getResult());
        assertEquals(1, records.writes());
    }

    @Test
    void recheckFailsWhenRequesterLostPermission() {
        String id = requestDelete(1001, "admin", null);
        properties.getToolGateway().setRoles(Map.of("admin", List.of("search_document"), "manager", List.of("search_document")));
        ApprovalRequest result = service.approve(id, "manager", null);
        assertEquals(ApprovalStatus.FAILED, result.getStatus());
        assertEquals("PERMISSION_DENIED", result.getFailureReason());
        assertEquals(0, records.writes());
    }

    @Test
    void recheckAppliesBasisVerification() {
        bases.put("req_aaaaaaaaaaaa", report(ClaimStatus.SUPPORTED, ClaimStatus.INSUFFICIENT));
        String id = requestDelete(1002, "admin", "req_aaaaaaaaaaaa");
        ApprovalRequest result = service.approve(id, "manager", null);
        assertEquals(ApprovalStatus.FAILED, result.getStatus());
        assertEquals("UNVERIFIED_BASIS", result.getFailureReason());
        assertFalse(records.findById(1002).orElseThrow().isDeleted());
    }

    @Test
    void executionFailureMarksApprovalFailed() {
        var failing = new InMemoryBusinessRecordRepository() {
            @Override
            public int softDelete(long recordId, OffsetDateTime now) {
                throw new IllegalStateException("db down");
            }
        };
        ToolGateway g = new ToolGateway(properties, catalog, failing);
        ToolExecutor x = new ToolExecutor(catalog, failing, retriever);
        ToolCallService t = new ToolCallService(g, x, approvals, requestId -> Optional.empty(), audit, properties);
        ApprovalService s = new ApprovalService(approvals, g, x, requestId -> Optional.empty(), audit, properties);
        String id = t.execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), "admin").getApprovalId();

        ApprovalRequest result = s.approve(id, "manager", null);
        assertEquals(ApprovalStatus.FAILED, result.getStatus());
        assertEquals(ApprovalService.EXECUTION_FAILED, result.getFailureReason());
        AuditEvent executed = events.get(events.size() - 1);
        assertEquals(AuditEventType.TOOL_CALL_EXECUTED, executed.getEventType());
        assertFalse(executed.isSuccess());
    }

    @Test
    void alreadyDecidedApprovalCannotBeDecidedAgain() {
        String id = requestDelete(1001, "admin", null);
        service.approve(id, "manager", null);
        BusinessException again = fails(() -> service.approve(id, "manager", null));
        assertEquals(ErrorCode.APPROVAL_NOT_PENDING, again.getErrorCode());
        assertEquals(ErrorCode.APPROVAL_NOT_PENDING, fails(() -> service.reject(id, "manager", null)).getErrorCode());
        assertEquals(1, records.writes());
    }

    @Test
    void concurrentClaimOnlyOneWins() {
        String id = requestDelete(1001, "admin", null);
        // 模拟另一位审批人在本次检查之后、抢占之前已处理
        var racing = new InMemoryApprovalRepository() {
            @Override
            public int claim(String approvalId, String approver, String comment, OffsetDateTime now) {
                return 0;
            }
        };
        racing.insert(approvals.findById(id).orElseThrow(), "req_x");
        ApprovalService s = new ApprovalService(racing, gateway, executor, requestId -> Optional.empty(), audit, properties);
        BusinessException e = fails(() -> s.approve(id, "manager", null));
        assertEquals(ErrorCode.APPROVAL_NOT_PENDING, e.getErrorCode());
        assertEquals(0, records.writes());
    }

    @Test
    void expiredApprovalCannotBeApprovedAndIsListedAsExpired() {
        String id = requestDelete(1001, "admin", null);
        approvals.forceExpiresAt(id, OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1));

        BusinessException e = fails(() -> service.approve(id, "manager", null));
        assertEquals(ErrorCode.APPROVAL_NOT_PENDING, e.getErrorCode());
        assertEquals("审批单不是待审批状态：审批单已过期。", e.getMessage());
        assertEquals(ApprovalStatus.EXPIRED, service.get(id).getStatus());
        assertEquals(1, service.list(ApprovalStatus.EXPIRED).size());
        assertEquals(0, service.list(ApprovalStatus.PENDING).size());
        assertEquals(0, records.writes());
    }

    @Test
    void listIsNewestFirstAndFiltersByStatus() {
        String a = requestDelete(1001, "admin", null);
        String b = requestDelete(1002, "admin", null);
        service.reject(a, "manager", null);
        assertEquals(2, service.list(null).size());
        assertEquals(List.of(b), service.list(ApprovalStatus.PENDING).stream().map(ApprovalRequest::getApprovalId).toList());
        assertEquals(List.of(a), service.list(ApprovalStatus.REJECTED).stream().map(ApprovalRequest::getApprovalId).toList());
    }

    @Test
    void unknownApprovalIs404() {
        assertEquals(ErrorCode.APPROVAL_NOT_FOUND, fails(() -> service.get("apr_000000000000")).getErrorCode());
        assertEquals(ErrorCode.APPROVAL_NOT_FOUND, fails(() -> service.approve("apr_000000000000", "manager", null)).getErrorCode());
    }
}
