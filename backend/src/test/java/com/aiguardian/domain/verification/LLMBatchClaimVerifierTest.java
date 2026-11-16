package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LLMBatchClaimVerifierTest {
    static final String CLAIM = """
            {"claim":"年假15天","originalText":"年假15天","supported":true,
             "evidenceId":"doc001_chunk01","quote":"正式员工年假15天"}
            """;
    static VerificationInput input() {
        return VerificationInput.builder().requestId("req_test").mode(RunMode.MOCK).scenarioId("SC01")
                .question("年假？").answer("年假15天")
                .evidence(List.of(Evidence.builder().evidenceId("doc001_chunk01")
                        .documentTitle("手册").content("正式员工年假15天").build())).build();
    }
    LLMBatchClaimVerifier verifier(LLMClient client) {
        return new LLMBatchClaimVerifier(client, new ObjectMapper(), new AiGuardianProperties());
    }

    @Test void buildsRequestAndInitialReport() {
        var result = verifier(request -> {
            assertEquals("req_test", request.getRequestId());
            assertEquals(RunMode.MOCK, request.getMode());
            assertEquals("SC01", request.getScenarioId());
            assertEquals(LLMPurpose.VERIFY_CLAIMS, request.getPurpose());
            assertEquals(1, request.getAttempt());
            assertTrue(request.isJsonMode());
            assertEquals(0, request.getTemperature());
            assertEquals(45000, request.getTimeoutMs());
            assertTrue(request.getSystemPrompt().contains("JSON"));
            assertTrue(request.getUserPrompt().contains("doc001_chunk01"));
            return "{\"claims\":[" + CLAIM + "],\"truncated\":false}";
        }).verify(input());
        assertEquals(VerificationState.COMPLETED, result.getVerificationState());
        assertEquals("batch", result.getVerifierName());
        assertEquals(1, result.getLlmCalls());
        assertEquals(1, result.getSupportedClaims());
        assertNull(result.getClaims().get(0).getQuoteExists());
        assertNull(result.getClaims().get(0).getStartIndex());
    }

    @Test void retriesOnlyRecoverableResponses() {
        for (String first : List.of("not json", "{}", " ", "{\"claims\":[],\"truncated\":\"false\"}")) {
            var requests = new ArrayList<LLMRequest>();
            var result = verifier(r -> {
                requests.add(r);
                return requests.size() == 1 ? first : "{\"claims\":[],\"truncated\":false}";
            }).verify(input());
            assertEquals(VerificationState.NO_CLAIMS, result.getVerificationState());
            assertNull(result.getFailureReason());
            assertEquals(2, result.getLlmCalls());
            assertEquals(2, requests.get(1).getAttempt());
        }
    }

    @Test void exhaustedFailuresHaveNoClaimsAndCorrectReason() {
        for (String raw : List.of("oops", "{}", "{\"claims\":[{\"claim\":3}]}", "{\"claims\":[]} garbage")) {
            var result = verifier(r -> raw).verify(input());
            assertEquals(VerificationState.FAILED, result.getVerificationState());
            assertEquals(raw.equals("oops") || raw.endsWith("garbage") ? VerificationFailureReason.INVALID_JSON
                    : VerificationFailureReason.SCHEMA_INVALID, result.getFailureReason());
            assertEquals(2, result.getLlmCalls());
            assertTrue(result.getClaims().isEmpty());
            assertNull(result.getUnsupportedRatio());
        }
    }

    @Test void llmErrorsUseContractRetryPolicy() {
        for (var type : LLMException.Type.values()) {
            var result = verifier(r -> { throw new LLMException(type); }).verify(input());
            assertEquals(type == LLMException.Type.EMPTY_RESPONSE ? 2 : 1, result.getLlmCalls());
            assertEquals(switch (type) {
                case TIMEOUT -> VerificationFailureReason.LLM_TIMEOUT;
                case API_ERROR -> VerificationFailureReason.LLM_API_ERROR;
                case EMPTY_RESPONSE -> VerificationFailureReason.INVALID_JSON;
            }, result.getFailureReason());
        }
    }

    @Test void truncatesAndHonorsModelTruncation() {
        var result = verifier(r -> "{\"claims\":[" + String.join(",", Collections.nCopies(9, CLAIM)) + "]}")
                .verify(input());
        assertEquals(VerificationState.PARTIAL, result.getVerificationState());
        assertTrue(result.isTruncated());
        assertEquals(8, result.getTotalClaims());
        assertEquals("c8", result.getClaims().get(7).getClaimId());
        assertEquals(VerificationState.PARTIAL, verifier(r -> "{\"claims\":[" + CLAIM
                + "],\"truncated\":true}").verify(input()).getVerificationState());
    }

    @Test void unsupportedAndMissingCitationAreNotSupported() {
        String unsupported = CLAIM.replace("true", "false");
        String missing = CLAIM.replace("\"正式员工年假15天\"", "\" \"");
        var result = verifier(r -> "{\"claims\":[" + unsupported + "," + missing + "]}").verify(input());
        assertEquals(0, result.getSupportedClaims());
        assertEquals(InsufficientReason.MODEL_UNSUPPORTED, result.getClaims().get(0).getInsufficientReason());
        assertEquals(InsufficientReason.MODEL_NO_CITATION, result.getClaims().get(1).getInsufficientReason());
    }

    @Test void respectsConfiguredLimitsAndTimeout() {
        var properties = new AiGuardianProperties();
        properties.getVerification().setMaxClaims(1);
        properties.getLlm().setVerifyMaxAttempts(1);
        properties.getLlm().setVerifyTimeoutMs(1234);
        var result = new LLMBatchClaimVerifier(r -> {
            assertEquals(1234, r.getTimeoutMs());
            return "{\"claims\":[" + CLAIM + "," + CLAIM + "]}";
        }, new ObjectMapper(), properties).verify(input());
        assertEquals(1, result.getTotalClaims());
        assertEquals(VerificationState.PARTIAL, result.getVerificationState());
        var failed = new LLMBatchClaimVerifier(r -> "bad", new ObjectMapper(), properties).verify(input());
        assertEquals(1, failed.getLlmCalls());
    }

    @Test void realComponentsWireWithoutChangingSharedInterfaces() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(LLMClient.class, () -> r -> "{\"claims\":[" + CLAIM + "]}");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(AiGuardianProperties.class, () -> new AiGuardianProperties());
            context.register(LLMBatchClaimVerifier.class, ExactQuoteCitationValidator.class);
            context.refresh();
            var report = context.getBean(ClaimVerifier.class).verify(input());
            context.getBean(CitationValidator.class).validate(report, input().getEvidence());
            assertEquals(1, report.getSupportedClaims());
            assertTrue(report.getClaims().get(0).getQuoteExists());
        }
    }
}
