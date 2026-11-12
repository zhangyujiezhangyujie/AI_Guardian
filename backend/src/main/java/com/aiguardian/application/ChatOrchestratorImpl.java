package com.aiguardian.application;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.common.IdGenerator;
import com.aiguardian.common.ShanghaiTime;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.guard.OutputGuard;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.LLMPurpose;
import com.aiguardian.domain.model.LLMRequest;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.Timing;
import com.aiguardian.domain.model.VerificationInput;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.verification.CitationValidator;
import com.aiguardian.domain.verification.ClaimVerifier;
import com.aiguardian.infrastructure.llm.LLMClient;
import com.aiguardian.infrastructure.llm.LLMException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.aiguardian.domain.model.AuditEventType.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@Slf4j
@Service
public class ChatOrchestratorImpl implements ChatOrchestrator {
    private final AiGuardianProperties properties;
    private final EvidenceRetriever retriever;
    private final LLMClient llm;
    private final Map<String, ClaimVerifier> verifiers;
    private final CitationValidator citationValidator;
    private final OutputGuardRegistry guards;
    private final AuditService audit;
    private final ObjectMapper json;

    public ChatOrchestratorImpl(AiGuardianProperties properties, EvidenceRetriever retriever,
                                LLMClient llm, List<ClaimVerifier> verifiers, CitationValidator citationValidator,
                                OutputGuardRegistry guards, AuditService audit, ObjectMapper json) {
        this.properties = properties;
        this.retriever = retriever;
        this.llm = llm;
        this.verifiers = verifiers.stream()
                .collect(Collectors.toUnmodifiableMap(ClaimVerifier::name, Function.identity()));
        this.citationValidator = citationValidator;
        this.guards = guards;
        this.audit = audit;
        this.json = json;
    }

    @Override
    public ChatResponse handle(ChatCommand command) {
        GuardPolicy policy = resolvePolicy(command);
        OutputGuard guard = guards.get(policy.getStrategy());
        ClaimVerifier verifier = resolveVerifier(command);
        Exchange exchange = new Exchange(command);
        record(exchange, REQUEST_RECEIVED, true, null, null, details(
                "mode", command.getMode(), "scenarioId", command.getScenarioId(),
                "questionLength", command.getQuestion().length()));

        String stage = "RETRIEVAL";
        try {
            retrieve(exchange);
            if (exchange.evidence.isEmpty()) {
                exchange.report = VerificationReport.noEvidence(exchange.requestId);
            } else {
                stage = "GENERATION";
                generate(exchange);
                stage = "INTERNAL";
                verify(exchange, verifier);
            }
            stage = "INTERNAL";
            return finish(exchange, policy, guard);
        } catch (RuntimeException e) {
            ErrorCode code = switch (stage) {
                case "RETRIEVAL" -> ErrorCode.RETRIEVAL_FAILED;
                case "GENERATION" -> e instanceof LLMException
                        ? ErrorCode.LLM_GENERATION_FAILED : ErrorCode.INTERNAL_ERROR;
                default -> ErrorCode.INTERNAL_ERROR;
            };
            if (code == ErrorCode.INTERNAL_ERROR) {
                log.error("requestId={} stage={} exceptionType={}", exchange.requestId, stage, e.getClass().getName());
            }
            save(exchange, code.name());
            record(exchange, REQUEST_FAILED, false, null, code.name(), details(
                    "errorCode", code.name(), "stage", code == ErrorCode.INTERNAL_ERROR ? "INTERNAL" : stage));
            throw new BusinessException(code, exchange.requestId);
        }
    }

    private void retrieve(Exchange exchange) {
        int topK = properties.getRetrieval().getTopK();
        long started = System.nanoTime();
        try {
            exchange.evidence = retriever.retrieve(exchange.command.getQuestion(), topK);
        } finally {
            exchange.timing.setRetrievalMs(elapsed(started));
        }
        record(exchange, RETRIEVAL_DONE, true, exchange.timing.getRetrievalMs(), null, details(
                "topK", topK, "hitCount", exchange.evidence.size(),
                "evidenceIds", exchange.evidence.stream().map(Evidence::getEvidenceId).toList(),
                "topScore", exchange.evidence.isEmpty() ? null : exchange.evidence.get(0).getScore()));
    }

    private void generate(Exchange exchange) {
        ChatCommand command = exchange.command;
        LLMRequest request = LLMRequest.builder().requestId(exchange.requestId)
                .mode(command.getMode()).scenarioId(command.getScenarioId())
                .purpose(LLMPurpose.GENERATE_ANSWER).attempt(1).temperature(0.3).jsonMode(false)
                .systemPrompt(PromptTemplates.GENERATE_SYSTEM_PROMPT)
                .userPrompt(PromptTemplates.generateUserPrompt(command.getQuestion(), exchange.evidence))
                .timeoutMs(properties.getLlm().getGenerateTimeoutMs())
                .build();
        long started = System.nanoTime();
        try {
            exchange.answer = llm.generate(request);
        } catch (LLMException e) {
            record(exchange, GENERATION_FAILED, false, elapsed(started), ErrorCode.LLM_GENERATION_FAILED.name(),
                    details("llmErrorType", e.getType().name()));
            throw e;
        } finally {
            exchange.timing.setGenerationMs(elapsed(started));
        }
        record(exchange, GENERATION_DONE, true, exchange.timing.getGenerationMs(), null,
                details("answerLength", exchange.answer.length()));
    }

    private void verify(Exchange exchange, ClaimVerifier verifier) {
        if (verifier == null) {
            exchange.report = VerificationReport.builder().requestId(exchange.requestId).answer(exchange.answer)
                    .verificationState(VerificationState.NO_CLAIMS).verifierName("none").build();
            return;
        }
        long started = System.nanoTime();
        ChatCommand command = exchange.command;
        exchange.report = verifier.verify(VerificationInput.builder()
                .requestId(exchange.requestId).mode(command.getMode()).scenarioId(command.getScenarioId())
                .question(command.getQuestion()).answer(exchange.answer).evidence(exchange.evidence).build());

        VerificationReport report = exchange.report;
        String failure = report.getFailureReason() == null ? null : report.getFailureReason().name();
        record(exchange, VERIFICATION_DONE, report.getVerificationState() != VerificationState.FAILED,
                elapsed(started), failure, details("verifierName", report.getVerifierName(),
                        "state", report.getVerificationState(), "failureReason", failure,
                        "totalClaims", report.getTotalClaims(), "llmCalls", report.getLlmCalls()));

        if (report.getVerificationState() == VerificationState.COMPLETED
                || report.getVerificationState() == VerificationState.PARTIAL) {
            long citationStarted = System.nanoTime();
            exchange.report = citationValidator.validate(report, exchange.evidence);
            record(exchange, CITATION_CHECKED, true, elapsed(citationStarted), null, citationDetails(exchange.report));
        }
        exchange.timing.setVerificationMs(elapsed(started));
    }

    private Map<String, Object> citationDetails(VerificationReport report) {
        int checked = 0;
        int passed = 0;
        Map<String, Integer> failed = new LinkedHashMap<>();
        for (ClaimVerification claim : report.getClaims()) {
            Boolean exists = claim.getQuoteExists();
            if (exists != null) {
                checked++;
                if (exists) {
                    passed++;
                } else if (claim.getInsufficientReason() != null) {
                    failed.merge(claim.getInsufficientReason().name(), 1, Integer::sum);
                }
            }
        }
        return details("checkedCount", checked, "passedCount", passed, "failedReasons", failed);
    }

    private ChatResponse finish(Exchange exchange, GuardPolicy policy, OutputGuard guard) {
        ChatCommand command = exchange.command;
        long started = System.nanoTime();
        GuardResult decision = guard.evaluate(exchange.report, policy);
        exchange.guard = decision;
        record(exchange, GUARD_APPLIED, true, elapsed(started), null, details(
                "strategy", decision.getStrategy(), "blockThreshold", decision.getBlockThreshold(),
                "action", decision.getAction(), "unsupportedRatio", decision.getUnsupportedRatio(),
                "overridden", command.getGuardStrategyOverride() != null || command.getBlockThresholdOverride() != null));

        VerificationReport responseReport = exchange.report;
        if (decision.getAction() == GuardAction.BLOCKED || !decision.isAnswerVisible()) {
            responseReport = json.convertValue(exchange.report, VerificationReport.class);
            responseReport.setAnswer(null);
            for (ClaimVerification claim : responseReport.getClaims()) {
                claim.setOriginalText(null);
                claim.setStartIndex(null);
                claim.setEndIndex(null);
            }
        }

        ChatResponse response = ChatResponse.builder().requestId(exchange.requestId).sessionId(exchange.sessionId)
                .mode(command.getMode()).scenarioId(command.getScenarioId()).question(command.getQuestion())
                .evidence(exchange.evidence).verification(responseReport).guard(decision)
                .timing(exchange.timing).createdAt(exchange.createdAt.toString()).build();
        save(exchange, null);
        record(exchange, REQUEST_COMPLETED, true, exchange.timing.getTotalMs(), null,
                details("totalMs", exchange.timing.getTotalMs(), "action", decision.getAction()));
        return response;
    }

    private GuardPolicy resolvePolicy(ChatCommand command) {
        var defaults = properties.getOutputGuard();
        String strategy = command.getGuardStrategyOverride() == null
                ? defaults.getStrategy() : command.getGuardStrategyOverride();
        double threshold = command.getBlockThresholdOverride() == null
                ? defaults.getBlockThreshold() : command.getBlockThresholdOverride();
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
            throw BusinessException.invalidRequest("blockThreshold 必须在 0 到 1 之间。");
        }
        return new GuardPolicy(strategy, threshold);
    }

    private ClaimVerifier resolveVerifier(ChatCommand command) {
        String override = command.getVerifierOverride();
        if (command.getMode() == RunMode.MOCK && override != null) {
            throw BusinessException.invalidRequest("只有 REAL 模式允许覆盖核验器。");
        }
        if ("none".equals(override)) {
            return null;
        }
        String name = override == null ? properties.getVerification().getVerifier() : override;
        ClaimVerifier verifier = verifiers.get(name);
        if (verifier == null) {
            if (override == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            throw BusinessException.invalidRequest("指定的核验器不可用。");
        }
        return verifier;
    }

    private void save(Exchange exchange, String errorCode) {
        exchange.timing.setTotalMs(elapsed(exchange.started));
        ChatCommand command = exchange.command;
        RequestRecord request = RequestRecord.builder().requestId(exchange.requestId).sessionId(exchange.sessionId)
                .mode(command.getMode()).scenarioId(command.getScenarioId()).question(command.getQuestion())
                .rawAnswer(exchange.answer).evidence(exchange.evidence).report(exchange.report).guard(exchange.guard)
                .timing(exchange.timing).errorCode(errorCode).createdAt(exchange.createdAt).build();
        writeAudit(exchange.requestId, () -> audit.saveRequestRecord(request));
    }

    private void record(Exchange exchange, AuditEventType type, boolean success, Long duration, String errorCode,
                        Map<String, Object> detail) {
        AuditEvent event = AuditEvent.builder().requestId(exchange.requestId).eventType(type).success(success)
                .durationMs(duration).errorCode(errorCode).detail(detail).createdAt(ShanghaiTime.now()).build();
        writeAudit(exchange.requestId, () -> audit.record(event));
    }

    private void writeAudit(String requestId, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("requestId={} auditWriteFailed exceptionType={}", requestId, e.getClass().getName());
        }
    }

    private static long elapsed(long started) {
        return NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static Map<String, Object> details(Object... entries) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            detail.put((String) entries[index], entries[index + 1]);
        }
        return detail;
    }

    private static final class Exchange {
        private final long started = System.nanoTime();
        private final OffsetDateTime createdAt = ShanghaiTime.now();
        private final String requestId = IdGenerator.requestId();
        private final String sessionId;
        private final ChatCommand command;
        private final Timing timing = new Timing();
        private List<Evidence> evidence = List.of();
        private String answer;
        private VerificationReport report;
        private GuardResult guard;

        private Exchange(ChatCommand command) {
            this.command = command;
            this.sessionId = command.getSessionId() == null ? IdGenerator.sessionId() : command.getSessionId();
        }
    }
}
