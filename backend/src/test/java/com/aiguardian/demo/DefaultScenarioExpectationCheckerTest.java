package com.aiguardian.demo;

import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultScenarioExpectationCheckerTest {
    private final DefaultScenarioExpectationChecker checker = new DefaultScenarioExpectationChecker();

    @Test
    void matchesClaimsAndTheStrategyActuallyUsed() {
        Scenario scenario = scenario("餐费补贴", "SUPPORTED", null, "block", "BLOCKED");
        ChatResponse response = ChatResponse.builder()
                .verification(VerificationReport.builder()
                        .verificationState(VerificationState.COMPLETED)
                        .claims(List.of(ClaimVerification.builder()
                                .claim("员工每月有300元餐费补贴")
                                .status(ClaimStatus.SUPPORTED)
                                .build()))
                        .build())
                .guard(GuardResult.builder().strategy("block").action(GuardAction.BLOCKED).build())
                .build();

        ScenarioCheckResult result = checker.check(scenario, response, 200, null);

        assertTrue(result.checked());
        assertTrue(result.passed());
        assertTrue(result.mismatches().isEmpty());
    }

    @Test
    void returnsReadableMismatchesForUnexpectedStatusAndClaim() {
        Scenario scenario = scenario("交通补贴", "INSUFFICIENT", "MODEL_UNSUPPORTED", "annotate", "ANNOTATED");
        ChatResponse response = ChatResponse.builder()
                .verification(VerificationReport.builder()
                        .verificationState(VerificationState.COMPLETED)
                        .claims(List.of(ClaimVerification.builder()
                                .claim("员工每月有交通补贴")
                                .status(ClaimStatus.SUPPORTED)
                                .build()))
                        .build())
                .guard(GuardResult.builder().strategy("annotate").action(GuardAction.ANNOTATED).build())
                .build();

        ScenarioCheckResult result = checker.check(scenario, response, 500, "INTERNAL_ERROR");

        assertFalse(result.passed());
        assertTrue(result.mismatches().stream().anyMatch(mismatch -> mismatch.startsWith("httpStatus")));
        assertTrue(result.mismatches().stream().anyMatch(mismatch -> mismatch.contains("claims[0].status")));
        assertTrue(result.mismatches().stream().anyMatch(mismatch -> mismatch.contains("insufficientReason")));
    }

    @Test
    void reportsNotCheckedWhenScenarioHasNoExpectedValues() {
        ScenarioCheckResult result = checker.check(Scenario.builder().build(), null, 200, null);

        assertFalse(result.checked());
        assertNull(result.passed());
        assertTrue(result.mismatches().isEmpty());
    }

    private static Scenario scenario(String claimContains, String status, String reason,
                                     String strategy, String action) {
        return Scenario.builder()
                .expected(ScenarioExpected.builder()
                        .httpStatus(200)
                        .verificationState("COMPLETED")
                        .claims(List.of(ExpectedClaim.builder()
                                .claimContains(claimContains)
                                .status(status)
                                .insufficientReason(reason)
                                .build()))
                        .guardByStrategy(Map.of(strategy, action))
                        .build())
                .build();
    }
}
