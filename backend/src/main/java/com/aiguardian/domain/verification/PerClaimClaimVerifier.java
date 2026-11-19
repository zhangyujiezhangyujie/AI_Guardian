package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.LLMClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 性能方案 B：一次提取，再按顺序逐条调用；所有尝试都计入 llmCalls。 */
@Component
@Profile("!stub")
public class PerClaimClaimVerifier implements ClaimVerifier {
    private final LLMClient llm;
    private final ObjectMapper json;
    private final AiGuardianProperties properties;

    public PerClaimClaimVerifier(LLMClient llm, ObjectMapper json, AiGuardianProperties properties) {
        this.llm = llm;
        this.json = json;
        this.properties = properties;
    }

    @Override public String name() { return "per-claim"; }

    @Override
    public VerificationReport verify(VerificationInput input) {
        int limit = Math.max(1, Math.min(8, properties.getVerification().getMaxClaims()));
        var extracted = new LLMBatchClaimVerifier(request -> {
            request.setSystemPrompt(VerificationPrompts.extraction(limit));
            return llm.generate(request);
        }, json, stageProperties(limit)).verify(input);
        extracted.setVerifierName(name());
        if (extracted.getVerificationState() == VerificationState.FAILED || extracted.getClaims().isEmpty()) {
            return extracted;
        }
        int calls = extracted.getLlmCalls();
        var checked = new ArrayList<ClaimVerification>();
        for (var target : extracted.getClaims()) {
            var single = new LLMBatchClaimVerifier(request -> {
                request.setSystemPrompt(VerificationPrompts.single());
                try {
                    var data = (ObjectNode) json.readTree(request.getUserPrompt());
                    var node = data.putObject("target");
                    node.put("claim", target.getClaim());
                    node.put("originalText", target.getOriginalText());
                    request.setUserPrompt(json.writeValueAsString(data));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("无法构造逐条核验输入", e);
                }
                String raw = llm.generate(request);
                // 除通用结构检查外，不能接受模型换了一条结论或返回多条结果。
                try {
                    var root = json.readTree(raw == null ? "" : raw);
                    if (root != null && root.path("claims").isArray()) {
                        var claims = root.get("claims");
                        if (claims.size() != 1 || root.path("truncated").asBoolean()
                                || !target.getClaim().equals(claims.path(0).path("claim").asText())
                                || !target.getOriginalText().equals(claims.path(0).path("originalText").asText())) {
                            return "{}"; // 由共享解析器按 SCHEMA_INVALID 执行有限重试。
                        }
                    }
                } catch (JsonProcessingException e) {
                    // 交给共享解析器区分 INVALID_JSON，避免改变批量版的异常语义。
                }
                return raw;
            }, json, stageProperties(1)).verify(input);
            calls += single.getLlmCalls();
            if (single.getVerificationState() == VerificationState.FAILED) {
                single.setVerifierName(name());
                single.setLlmCalls(calls);
                return single; // 失败报告不保留前面成功结论，避免展示为已完成核验。
            }
            var claim = single.getClaims().get(0);
            claim.setClaimId("c" + (checked.size() + 1));
            checked.add(claim);
        }
        extracted.setClaims(checked);
        extracted.setLlmCalls(calls);
        extracted.recalculate();
        return extracted;
    }

    private AiGuardianProperties stageProperties(int limit) {
        var config = new AiGuardianProperties();
        config.getVerification().setMaxClaims(limit);
        config.getLlm().setVerifyMaxAttempts(properties.getLlm().getVerifyMaxAttempts());
        config.getLlm().setVerifyTimeoutMs(properties.getLlm().getVerifyTimeoutMs());
        return config;
    }
}
