package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.LLMClient;
import com.aiguardian.infrastructure.llm.LLMException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.util.ArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!stub")
public class LLMBatchClaimVerifier implements ClaimVerifier {
    private final LLMClient llm;
    private final ObjectMapper json;
    private final AiGuardianProperties properties;

    public LLMBatchClaimVerifier(LLMClient llm, ObjectMapper json, AiGuardianProperties properties) {
        this.llm = llm;
        this.json = json;
        this.properties = properties;
    }

    @Override public String name() { return "batch"; }

    @Override
    public VerificationReport verify(VerificationInput input) {
        var report = VerificationReport.builder().requestId(input == null ? null : input.getRequestId())
                .answer(input == null ? null : input.getAnswer()).verifierName(name())
                .verificationState(VerificationState.FAILED).build();
        int limit = Math.max(1, Math.min(8, properties.getVerification().getMaxClaims()));
        int attempts = Math.max(1, properties.getLlm().getVerifyMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var request = LLMRequest.builder().requestId(input.getRequestId()).mode(input.getMode())
                        .scenarioId(input.getScenarioId()).purpose(LLMPurpose.VERIFY_CLAIMS).attempt(attempt)
                        .systemPrompt(VerificationPrompts.system(limit)).userPrompt(VerificationPrompts.user(input, json))
                        .temperature(0.0).jsonMode(true).timeoutMs(properties.getLlm().getVerifyTimeoutMs()).build();
                report.setLlmCalls(report.getLlmCalls() + 1);
                String raw = llm.generate(request);
                if (raw == null || raw.isBlank()) {
                    report.setFailureReason(VerificationFailureReason.INVALID_JSON);
                    continue;
                }
                JsonNode root;
                try {
                    root = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
                } catch (JsonProcessingException e) {
                    report.setFailureReason(VerificationFailureReason.INVALID_JSON);
                    continue;
                }
                if (!valid(root)) {
                    report.setFailureReason(VerificationFailureReason.SCHEMA_INVALID);
                    continue;
                }
                var claims = new ArrayList<ClaimVerification>();
                var nodes = root.get("claims");
                for (int i = 0; i < Math.min(limit, nodes.size()); i++) {
                    var node = nodes.get(i);
                    boolean supported = node.get("supported").booleanValue();
                    String evidenceId = nullableText(node.get("evidenceId"));
                    String quote = nullableText(node.get("quote"));
                    boolean missing = evidenceId == null || evidenceId.isBlank() || quote == null || quote.isBlank();
                    claims.add(ClaimVerification.builder().claimId("c" + (i + 1))
                            .claim(node.get("claim").textValue()).originalText(node.get("originalText").textValue())
                            .modelSupported(supported).evidenceId(evidenceId).quote(quote)
                            .status(supported && !missing ? ClaimStatus.SUPPORTED : ClaimStatus.INSUFFICIENT)
                            .insufficientReason(!supported ? InsufficientReason.MODEL_UNSUPPORTED
                                    : missing ? InsufficientReason.MODEL_NO_CITATION : null).build());
                }
                report.setClaims(claims);
                report.setTruncated(root.path("truncated").asBoolean() || nodes.size() > limit);
                report.setVerificationState(report.isTruncated() ? VerificationState.PARTIAL
                        : claims.isEmpty() ? VerificationState.NO_CLAIMS : VerificationState.COMPLETED);
                report.setFailureReason(null);
                report.recalculate();
                return report;
            } catch (LLMException e) {
                if (e.getType() == LLMException.Type.EMPTY_RESPONSE) {
                    report.setFailureReason(VerificationFailureReason.INVALID_JSON);
                    continue;
                }
                report.setFailureReason(e.getType() == LLMException.Type.TIMEOUT
                        ? VerificationFailureReason.LLM_TIMEOUT : VerificationFailureReason.LLM_API_ERROR);
                return report;
            } catch (Exception e) {
                // 不把异常消息或模型原文写入日志，避免泄露输入和凭据。
                report.setFailureReason(VerificationFailureReason.SCHEMA_INVALID);
                return report;
            }
        }
        return report;
    }

    private static boolean valid(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("claims").isArray()
                || (root.has("truncated") && !root.get("truncated").isBoolean())) return false;
        for (var node : root.get("claims")) {
            if (!node.isObject() || !text(node.get("claim")) || !text(node.get("originalText"))
                    || !node.path("supported").isBoolean()) return false;
            for (String key : new String[]{"evidenceId", "quote"}) {
                var value = node.get(key);
                if (value != null && !value.isNull() && !value.isTextual()) return false;
            }
        }
        return true;
    }

    private static boolean text(JsonNode value) {
        return value != null && value.isTextual() && !value.textValue().isBlank();
    }

    private static String nullableText(JsonNode value) {
        return value == null || value.isNull() ? null : value.textValue();
    }
}
