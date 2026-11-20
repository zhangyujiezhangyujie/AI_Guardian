package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerClaimClaimVerifierTest {
    final ObjectMapper json = new ObjectMapper();
    static final String C1 = LLMBatchClaimVerifierTest.CLAIM;
    static final String C2 = C1.replace("年假15天", "年假30天").replace("true", "false");
    static String response(String... claims) { return "{\"claims\":[" + String.join(",", claims) + "]}"; }
    PerClaimClaimVerifier verifier(LLMClient client) {
        return new PerClaimClaimVerifier(client, json, new AiGuardianProperties());
    }

    @Test void extractsOnceThenVerifiesEachTargetWithContext() throws Exception {
        var requests = new ArrayList<LLMRequest>();
        var result = verifier(r -> {
            requests.add(r);
            return switch (requests.size()) {
                case 1 -> response(C1, C2);
                case 2 -> response(C1);
                default -> response(C2);
            };
        }).verify(LLMBatchClaimVerifierTest.input());
        assertEquals("per-claim", result.getVerifierName());
        assertEquals(VerificationState.COMPLETED, result.getVerificationState());
        assertEquals(3, result.getLlmCalls());
        assertEquals(2, result.getTotalClaims());
        assertEquals(1, result.getSupportedClaims());
        assertEquals("c2", result.getClaims().get(1).getClaimId());
        assertNull(result.getClaims().get(0).getQuoteExists());
        assertTrue(requests.get(0).getSystemPrompt().contains("仅提取"));
        for (int i = 0; i < requests.size(); i++) {
            var r = requests.get(i);
            assertEquals("req_test", r.getRequestId());
            assertEquals(RunMode.MOCK, r.getMode());
            assertEquals("SC01", r.getScenarioId());
            assertEquals(LLMPurpose.VERIFY_CLAIMS, r.getPurpose());
            assertTrue(r.isJsonMode());
            assertEquals(45000, r.getTimeoutMs());
            assertEquals(1, r.getAttempt());
            if (i > 0) {
                var data = json.readTree(r.getUserPrompt());
                assertEquals(i == 1 ? "年假15天" : "年假30天", data.path("target").path("claim").asText());
                assertEquals("年假15天", data.path("answer").asText());
            }
        }
    }

    @Test void countsExtractionAndSingleRetriesSeparately() {
        var requests = new ArrayList<LLMRequest>();
        var result = verifier(r -> {
            requests.add(r);
            return switch (requests.size()) {
                case 1 -> "bad json";
                case 2 -> response(C1);
                case 3 -> "{}";
                default -> response(C1);
            };
        }).verify(LLMBatchClaimVerifierTest.input());
        assertEquals(VerificationState.COMPLETED, result.getVerificationState());
        assertEquals(4, result.getLlmCalls());
        assertEquals(List.of(1, 2, 1, 2), requests.stream().map(LLMRequest::getAttempt).toList());
    }

    @Test void extractionFailureOrNoClaimsStopsImmediately() {
        var failed = verifier(r -> { throw new LLMException(LLMException.Type.TIMEOUT); })
                .verify(LLMBatchClaimVerifierTest.input());
        assertEquals(VerificationFailureReason.LLM_TIMEOUT, failed.getFailureReason());
        assertEquals(1, failed.getLlmCalls());
        var empty = verifier(r -> response()).verify(LLMBatchClaimVerifierTest.input());
        assertEquals(VerificationState.NO_CLAIMS, empty.getVerificationState());
        assertEquals(1, empty.getLlmCalls());
        assertEquals("per-claim", empty.getVerifierName());
    }

    @Test void oneFailureDiscardsPreviousSuccessesAndKeepsTotalCalls() {
        var calls = new AtomicInteger();
        var result = verifier(r -> {
            int n = calls.incrementAndGet();
            if (n == 1) return response(C1, C2);
            if (n == 2) return response(C1);
            throw new LLMException(LLMException.Type.API_ERROR);
        }).verify(LLMBatchClaimVerifierTest.input());
        assertEquals(VerificationState.FAILED, result.getVerificationState());
        assertEquals(VerificationFailureReason.LLM_API_ERROR, result.getFailureReason());
        assertEquals(3, result.getLlmCalls());
        assertEquals("per-claim", result.getVerifierName());
        assertTrue(result.getClaims().isEmpty());
        assertEquals(0, result.getTotalClaims());
        assertNull(result.getUnsupportedRatio());
    }

    @Test void refusesChangedTargetsEmptyMultipleAndTruncatedSingleResponses() {
        for (String raw : List.of(response(), response(C2), response(C1, C1),
                response(C1).replace("年假15天", "修改后的结论"),
                "{\"claims\":[" + C1 + "],\"truncated\":true}")) {
            var calls = new AtomicInteger();
            var result = verifier(r -> calls.incrementAndGet() == 1 ? response(C1) : raw)
                    .verify(LLMBatchClaimVerifierTest.input());
            assertEquals(VerificationState.FAILED, result.getVerificationState());
            assertEquals(VerificationFailureReason.SCHEMA_INVALID, result.getFailureReason());
            assertEquals(3, result.getLlmCalls());
        }
    }

    @Test void truncatesExtractionToEightAndPreservesPartialState() {
        var calls = new AtomicInteger();
        var result = verifier(r -> calls.incrementAndGet() == 1
                ? response(Collections.nCopies(9, C1).toArray(String[]::new)) : response(C1))
                .verify(LLMBatchClaimVerifierTest.input());
        assertEquals(VerificationState.PARTIAL, result.getVerificationState());
        assertTrue(result.isTruncated());
        assertEquals(8, result.getTotalClaims());
        assertEquals(9, result.getLlmCalls());
    }

    @Test void respectsTimeoutAttemptAndClaimSettings() {
        var config = new AiGuardianProperties();
        config.getLlm().setVerifyTimeoutMs(1234);
        config.getLlm().setVerifyMaxAttempts(1);
        config.getVerification().setMaxClaims(1);
        var calls = new AtomicInteger();
        var result = new PerClaimClaimVerifier(r -> {
            assertEquals(1234, r.getTimeoutMs());
            return calls.incrementAndGet() == 1 ? response(C1, C2) : "broken";
        }, json, config).verify(LLMBatchClaimVerifierTest.input());
        assertEquals(2, result.getLlmCalls());
        assertEquals(VerificationFailureReason.INVALID_JSON, result.getFailureReason());
    }

    @Test void bothImplementationsRegisterByDistinctNames() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(LLMClient.class, () -> r -> response());
            context.registerBean(ObjectMapper.class, () -> json);
            context.registerBean(AiGuardianProperties.class, () -> new AiGuardianProperties());
            context.register(LLMBatchClaimVerifier.class, PerClaimClaimVerifier.class);
            context.refresh();
            assertEquals(Set.of("batch", "per-claim"), context.getBeansOfType(ClaimVerifier.class)
                    .values().stream().map(ClaimVerifier::name).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
