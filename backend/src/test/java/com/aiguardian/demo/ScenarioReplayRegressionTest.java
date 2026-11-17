package com.aiguardian.demo;

import com.aiguardian.application.ChatCommand;
import com.aiguardian.application.ChatOrchestratorImpl;
import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.guard.AnnotateOutputGuard;
import com.aiguardian.domain.guard.BlockOutputGuard;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.verification.CitationValidator;
import com.aiguardian.domain.verification.ClaimVerifier;
import com.aiguardian.domain.verification.ExactQuoteCitationValidator;
import com.aiguardian.domain.verification.LLMBatchClaimVerifier;
import com.aiguardian.infrastructure.llm.MockLLMClient;
import com.aiguardian.infrastructure.lucene.Chunker;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseIndex;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseLoader;
import com.aiguardian.infrastructure.lucene.LuceneEvidenceRetriever;
import com.aiguardian.stub.LoggingAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline end-to-end replay of each scenario JSON currently present in test-cases/. */
class ScenarioReplayRegressionTest {
    @Test
    void replaysAvailableScenariosAndComparesTheirDeclaredExpectations() {
        Path root = projectRoot();
        AiGuardianProperties properties = properties(root);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        JsonScenarioRepository scenarios = new JsonScenarioRepository(properties, json);
        scenarios.load();

        KnowledgeBaseIndex index = new KnowledgeBaseIndex(properties,
                new KnowledgeBaseLoader(properties), new Chunker(properties));
        index.build();
        try {
            EvidenceRetriever retriever = new LuceneEvidenceRetriever(index);
            MockLLMClient mockLLM = new MockLLMClient(scenarios);
            ClaimVerifier verifier = new LLMBatchClaimVerifier(mockLLM, json, properties);
            CitationValidator citationValidator = new ExactQuoteCitationValidator(properties);
            AnnotateOutputGuard annotate = new AnnotateOutputGuard();
            OutputGuardRegistry guards = new OutputGuardRegistry(List.of(
                    annotate, new BlockOutputGuard(annotate)));
            AuditService audit = new LoggingAuditService();
            var orchestrator = new ChatOrchestratorImpl(properties, retriever, mockLLM,
                    List.of(verifier), citationValidator, guards, audit, json);
            ScenarioExpectationChecker checker = new DefaultScenarioExpectationChecker();

            for (Scenario scenario : scenarios.findAll()) {
                for (String strategy : strategiesToReplay(scenario)) {
                    ChatResponse response = null;
                    int httpStatus = 200;
                    String errorCode = null;
                    try {
                        response = orchestrator.handle(ChatCommand.builder()
                                .question(scenario.getQuestion())
                                .mode(RunMode.MOCK)
                                .scenarioId(scenario.getScenarioId())
                                .guardStrategyOverride(strategy)
                                .build());
                    } catch (BusinessException e) {
                        httpStatus = e.getErrorCode().getHttpStatus().value();
                        errorCode = e.getErrorCode().name();
                    }

                    ScenarioCheckResult result = checker.check(scenario, response, httpStatus, errorCode);
                    assertTrue(result.checked(), scenario.getScenarioId() + " was not checked");
                    assertTrue(Boolean.TRUE.equals(result.passed()),
                            scenario.getScenarioId() + "/" + strategy + ": " + result.mismatches());
                }
            }
        } finally {
            index.close();
        }
    }

    private static List<String> strategiesToReplay(Scenario scenario) {
        Map<String, String> expectedStrategies = scenario.getExpected().getGuardByStrategy();
        if (expectedStrategies != null && !expectedStrategies.isEmpty()) {
            return new ArrayList<>(new TreeSet<>(expectedStrategies.keySet()));
        }
        List<String> recommended = scenario.getRecommendedStrategies();
        return recommended == null || recommended.isEmpty() ? List.of("annotate") : recommended;
    }

    private static AiGuardianProperties properties(Path root) {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir(root.resolve("knowledge-base").toString());
        properties.getRetrieval().setTopK(3);
        properties.getRetrieval().setMinScore(1.0);
        properties.getRetrieval().setChunkMinChars(200);
        properties.getRetrieval().setChunkMaxChars(400);
        properties.getRetrieval().setChunkOverlapChars(40);
        properties.getVerification().setVerifier("batch");
        properties.getVerification().setMaxClaims(8);
        properties.getVerification().setMinQuoteChars(6);
        properties.getLlm().setVerifyMaxAttempts(2);
        properties.getDemo().setTestCasesDir(root.resolve("test-cases").toString());
        properties.getOutputGuard().setStrategy("annotate");
        properties.getOutputGuard().setBlockThreshold(0.5);
        return properties;
    }

    private static Path projectRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(workingDirectory.resolve("knowledge-base"))
                && Files.isDirectory(workingDirectory.resolve("test-cases"))) {
            return workingDirectory;
        }
        Path parent = workingDirectory.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("knowledge-base"))
                && Files.isDirectory(parent.resolve("test-cases"))) {
            return parent;
        }
        throw new IllegalStateException("无法定位包含 knowledge-base/ 和 test-cases/ 的项目根目录。");
    }
}
