package com.aiguardian.demo;

import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Compares an observed demo run with the scenario's declared expected values. */
@Component
public class DefaultScenarioExpectationChecker implements ScenarioExpectationChecker {
    @Override
    public ScenarioCheckResult check(Scenario scenario, ChatResponse response, int httpStatus, String errorCode) {
        if (scenario == null || scenario.getExpected() == null) {
            return new ScenarioCheckResult(false, null, List.of());
        }

        ScenarioExpected expected = scenario.getExpected();
        List<String> mismatches = new ArrayList<>();
        compare("httpStatus", expected.getHttpStatus(), httpStatus, mismatches);
        compare("errorCode", expected.getErrorCode(), errorCode, mismatches);
        compareEvidenceIds(expected.getEvidenceIdsContain(), response, mismatches);
        compareVerification(expected, response, mismatches);
        compareClaims(expected.getClaims(), response, mismatches);
        compareGuard(expected.getGuardByStrategy(), response, mismatches);

        return new ScenarioCheckResult(true, mismatches.isEmpty(), mismatches);
    }

    private static void compareEvidenceIds(List<String> expectedIds, ChatResponse response,
                                           List<String> mismatches) {
        if (expectedIds == null || expectedIds.isEmpty()) {
            return;
        }
        Set<String> actualIds = new HashSet<>();
        if (response != null && response.getEvidence() != null) {
            for (Evidence evidence : response.getEvidence()) {
                if (evidence != null && evidence.getEvidenceId() != null) {
                    actualIds.add(evidence.getEvidenceId());
                }
            }
        }
        for (String expectedId : expectedIds) {
            if (!actualIds.contains(expectedId)) {
                mismatches.add("evidenceIdsContain 缺少 " + expectedId);
            }
        }
    }

    private static void compareVerification(ScenarioExpected expected, ChatResponse response,
                                            List<String> mismatches) {
        VerificationReport report = response == null ? null : response.getVerification();
        String actualState = report == null || report.getVerificationState() == null
                ? null : report.getVerificationState().name();
        String actualFailure = report == null || report.getFailureReason() == null
                ? null : report.getFailureReason().name();
        compare("verificationState", expected.getVerificationState(), actualState, mismatches);
        compare("failureReason", expected.getFailureReason(), actualFailure, mismatches);
    }

    private static void compareClaims(List<ExpectedClaim> expectedClaims, ChatResponse response,
                                      List<String> mismatches) {
        List<ExpectedClaim> expected = expectedClaims == null ? List.of() : expectedClaims;
        List<ClaimVerification> actual = actualClaims(response);
        if (expected.size() != actual.size()) {
            mismatches.add("claims 数量期望 " + expected.size() + "，实际 " + actual.size());
        }
        int compared = Math.min(expected.size(), actual.size());
        for (int index = 0; index < compared; index++) {
            ExpectedClaim expectedClaim = expected.get(index);
            ClaimVerification actualClaim = actual.get(index);
            String actualText = actualClaim == null ? null : actualClaim.getClaim();
            String needle = expectedClaim.getClaimContains();
            if (actualText == null || needle == null || !actualText.contains(needle)) {
                mismatches.add("claims[" + index + "].claim 期望包含 " + needle + "，实际 " + actualText);
            }
            compare("claims[" + index + "].status", expectedClaim.getStatus(),
                    actualClaim == null || actualClaim.getStatus() == null
                            ? null : actualClaim.getStatus().name(), mismatches);
            compare("claims[" + index + "].insufficientReason", expectedClaim.getInsufficientReason(),
                    actualClaim == null || actualClaim.getInsufficientReason() == null
                            ? null : actualClaim.getInsufficientReason().name(), mismatches);
        }
    }

    private static List<ClaimVerification> actualClaims(ChatResponse response) {
        if (response == null || response.getVerification() == null
                || response.getVerification().getClaims() == null) {
            return List.of();
        }
        return response.getVerification().getClaims();
    }

    private static void compareGuard(java.util.Map<String, String> expectedByStrategy,
                                     ChatResponse response, List<String> mismatches) {
        if (expectedByStrategy == null || expectedByStrategy.isEmpty()) {
            return;
        }
        GuardResult guard = response == null ? null : response.getGuard();
        String strategy = guard == null ? null : guard.getStrategy();
        if (strategy == null) {
            mismatches.add("guardByStrategy 无法读取实际策略");
            return;
        }
        if (!expectedByStrategy.containsKey(strategy)) {
            mismatches.add("guardByStrategy 未配置策略 " + strategy);
            return;
        }
        compare("guardByStrategy[" + strategy + "]", expectedByStrategy.get(strategy),
                guard.getAction() == null ? null : guard.getAction().name(), mismatches);
    }

    private static void compare(String field, Object expected, Object actual, List<String> mismatches) {
        if (!java.util.Objects.equals(expected, actual)) {
            mismatches.add(field + " 期望 " + expected + "，实际 " + actual);
        }
    }
}
