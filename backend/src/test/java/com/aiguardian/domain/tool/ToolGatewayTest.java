package com.aiguardian.domain.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.ToolDenyReason;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 契约 7.10 决策链：白名单 → 权限 → 参数 → 记录存在 → 审批 → 依据核验。 */
class ToolGatewayTest {

    private static final String BASIS = "req_aaaaaaaaaaaa";

    private final AiGuardianProperties properties = new AiGuardianProperties();

    private final InMemoryBusinessRecordRepository records = new InMemoryBusinessRecordRepository();

    private final ToolGateway gateway = new ToolGateway(properties, new ToolCatalog(), records);

    private ToolDecisionOutcome decide(String user, String tool, Map<String, Object> args, String basedOn,
                                       Supplier<Optional<VerificationReport>> basis) {
        return gateway.decide(new ToolCallRequest(tool, args, basedOn), user, basis);
    }

    private ToolDecisionOutcome decide(String user, String tool, Map<String, Object> args) {
        return decide(user, tool, args, null, Optional::empty);
    }

    static VerificationReport report(VerificationState state, ClaimStatus... statuses) {
        List<ClaimVerification> claims = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            claims.add(ClaimVerification.builder().claimId("c" + (i + 1)).claim("结论" + i).status(statuses[i]).build());
        }
        VerificationReport report = VerificationReport.builder()
                .requestId(BASIS)
                .verificationState(state)
                .claims(claims)
                .build();
        report.recalculate();
        return report;
    }

    @Test
    void searchIsAllowedForEveryRole() {
        assertEquals(ToolDecision.ALLOW, decide("user", "search_document", Map.of("keyword", "年假")).decision());
        assertEquals(ToolDecision.ALLOW, decide("manager", "search_document", Map.of("keyword", "报销")).decision());
    }

    @Test
    void unknownOrNonWhitelistedToolIsDeniedFirst() {
        ToolDecisionOutcome unknown = decide("manager", "drop_table", Map.of());
        assertEquals(ToolDecision.DENY, unknown.decision());
        assertEquals(ToolDenyReason.TOOL_NOT_ALLOWED, unknown.denyReason());
        assertEquals("工具不在白名单中，已拒绝。", unknown.message());

        properties.getToolGateway().setAllowedTools(List.of("search_document"));
        assertEquals(ToolDenyReason.TOOL_NOT_ALLOWED, decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED")).denyReason());
    }

    @Test
    void ordinaryUserCannotDeleteOrUpdate() {
        ToolDecisionOutcome delete = decide("user", "delete_record", Map.of("recordId", 1001));
        assertEquals(ToolDecision.DENY, delete.decision());
        assertEquals(ToolDenyReason.PERMISSION_DENIED, delete.denyReason());
        assertEquals(ToolDenyReason.PERMISSION_DENIED, decide("user", "update_record", Map.of("recordId", 1001, "status", "CLOSED")).denyReason());
        assertEquals(ToolDenyReason.PERMISSION_DENIED, decide("guest", "search_document", Map.of("keyword", "年假")).denyReason());
    }

    @Test
    void permissionIsCheckedBeforeArguments() {
        // 普通员工连参数都不合法时，先因权限被拒
        assertEquals(ToolDenyReason.PERMISSION_DENIED, decide("user", "delete_record", Map.of("recordId", "x")).denyReason());
    }

    @Test
    void invalidArgumentsAreDenied() {
        ToolDecisionOutcome outcome = decide("manager", "update_record", Map.of("recordId", "1001", "status", "DELETED"));
        assertEquals(ToolDenyReason.INVALID_ARGUMENTS, outcome.denyReason());
        assertNull(outcome.arguments());
    }

    @Test
    void missingOrDeletedRecordIsDenied() {
        assertEquals(ToolDenyReason.RECORD_NOT_FOUND, decide("manager", "update_record", Map.of("recordId", 9999, "status", "CLOSED")).denyReason());
        records.softDelete(1005, java.time.OffsetDateTime.now());
        assertEquals(ToolDenyReason.RECORD_NOT_FOUND, decide("manager", "update_record", Map.of("recordId", 1005, "status", "OPEN")).denyReason());
    }

    @Test
    void managerDeleteRequiresApprovalAndIsNotAllowed() {
        ToolDecisionOutcome outcome = decide("manager", "delete_record", Map.of("recordId", 1001));
        assertEquals(ToolDecision.REQUIRE_APPROVAL, outcome.decision());
        assertNull(outcome.denyReason());
        assertEquals("该操作需要部门经理审批，网关未执行。", outcome.message());
    }

    @Test
    void managerUpdateWithoutBasisIsAllowedAndBasisIsNotLookedUp() {
        AtomicInteger lookups = new AtomicInteger();
        ToolDecisionOutcome outcome = decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED"), null,
                () -> {
                    lookups.incrementAndGet();
                    return Optional.empty();
                });
        assertEquals(ToolDecision.ALLOW, outcome.decision());
        assertEquals(1002L, outcome.arguments().recordId());
        assertEquals(0, lookups.get());
    }

    @Test
    void writeBasedOnFullySupportedAnswerIsAllowed() {
        VerificationReport ok = report(VerificationState.COMPLETED, ClaimStatus.SUPPORTED, ClaimStatus.SUPPORTED);
        assertEquals(ToolDecision.ALLOW, decide("manager", "update_record", Map.of("recordId", 1003, "status", "ARCHIVED"), BASIS, () -> Optional.of(ok)).decision());
    }

    @Test
    void writeBasedOnAnswerWithInsufficientClaimsIsDenied() {
        VerificationReport partial = report(VerificationState.COMPLETED, ClaimStatus.SUPPORTED, ClaimStatus.INSUFFICIENT);
        ToolDecisionOutcome outcome = decide("manager", "update_record", Map.of("recordId", 1004, "status", "OPEN"), BASIS, () -> Optional.of(partial));
        assertEquals(ToolDenyReason.UNVERIFIED_BASIS, outcome.denyReason());
        assertEquals("该操作依据的回答存在未经证据支持的结论，已拒绝。", outcome.message());
    }

    @Test
    void writeBasedOnUnusableStatesIsDenied() {
        for (VerificationReport basis : List.of(
                report(VerificationState.PARTIAL, ClaimStatus.SUPPORTED),
                report(VerificationState.FAILED),
                report(VerificationState.NO_CLAIMS),
                report(VerificationState.NO_EVIDENCE))) {
            assertEquals(ToolDenyReason.UNVERIFIED_BASIS,
                    decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED"), BASIS, () -> Optional.of(basis)).denyReason(),
                    basis.getVerificationState().name());
        }
    }

    @Test
    void writeBasedOnMissingRecordIsDenied() {
        assertEquals(ToolDenyReason.BASIS_NOT_FOUND,
                decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED"), BASIS, Optional::empty).denyReason());
    }

    @Test
    void readToolIgnoresBasis() {
        VerificationReport bad = report(VerificationState.FAILED);
        assertEquals(ToolDecision.ALLOW, decide("user", "search_document", Map.of("keyword", "年假"), BASIS, () -> Optional.of(bad)).decision());
    }

    @Test
    void strictForToolCallDisabledSkipsBasisCheck() {
        properties.getOutputGuard().setStrictForToolCall(false);
        VerificationReport bad = report(VerificationState.COMPLETED, ClaimStatus.INSUFFICIENT);
        assertEquals(ToolDecision.ALLOW, decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED"), BASIS, () -> Optional.of(bad)).decision());
    }

    @Test
    void gatewayNeverWrites() {
        decide("user", "delete_record", Map.of("recordId", 1001));
        decide("manager", "delete_record", Map.of("recordId", 1001));
        decide("manager", "update_record", Map.of("recordId", 1002, "status", "CLOSED"));
        assertEquals(0, records.writes());
        assertTrue(records.findById(1001).orElseThrow().getStatus().equals("OPEN"));
    }
}
