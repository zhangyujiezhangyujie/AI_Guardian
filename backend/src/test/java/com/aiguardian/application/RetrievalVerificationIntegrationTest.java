package com.aiguardian.application;

import com.aiguardian.audit.AuditQueryService;
import com.aiguardian.audit.AuditService;
import com.aiguardian.demo.ScenarioRepository;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.InsufficientReason;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.tool.ApprovalRepository;
import com.aiguardian.domain.tool.BusinessRecordRepository;
import com.aiguardian.infrastructure.persistence.JdbcAuditPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class RetrievalVerificationIntegrationTest {
    @Autowired ChatOrchestrator orchestrator;
    @Autowired ScenarioRepository scenarios;
    // This test covers retrieval and verification; the member 4 MySQL integration test covers audit persistence.
    @MockBean AuditService audit;
    @MockBean AuditQueryService auditQueryService;
    @MockBean JdbcAuditPersistenceRepository auditPersistenceRepository;
    // P1 工具网关的模拟业务记录同样依赖数据库，本测试不涉及，按同样方式隔离。
    @MockBean BusinessRecordRepository businessRecordRepository;
    @MockBean ApprovalRepository approvalRepository;

    @Test
    void verifiesTheAnnualLeaveCitationAgainstTheLuceneResult() {
        var response = orchestrator.handle(commandFor("SC01"));

        assertThat(response.getVerification().getVerificationState()).isEqualTo(VerificationState.COMPLETED);
        assertThat(response.getVerification().getClaims()).singleElement().satisfies(claim -> {
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUPPORTED);
            assertThat(claim.getQuoteExists()).isTrue();
        });
    }

    @ParameterizedTest
    @CsvSource({"annotate,0.5,ANNOTATED", "block,0.5,ANNOTATED", "block,0.3,BLOCKED"})
    void verifiesSc02MealAllowanceAndAppliesBlockThreshold(String strategy, double threshold,
                                                         GuardAction expectedAction) {
        var command = commandFor("SC02");
        command.setGuardStrategyOverride(strategy);
        command.setBlockThresholdOverride(threshold);

        var response = orchestrator.handle(command);
        var report = response.getVerification();

        assertThat(response.getEvidence()).extracting(Evidence::getEvidenceId).contains("doc005_chunk01");
        assertThat(report.getVerificationState()).isEqualTo(VerificationState.COMPLETED);
        assertThat(report.getUnsupportedRatio()).isEqualTo(0.5);
        assertThat(report.getClaims()).satisfiesExactly(meal -> {
            assertThat(meal.getClaim()).contains("300", "餐费补贴");
            assertThat(meal.getEvidenceId()).isEqualTo("doc005_chunk01");
            assertThat(meal.getStatus()).isEqualTo(ClaimStatus.SUPPORTED);
            assertThat(meal.getQuoteExists()).isTrue();
            assertThat(meal.getInsufficientReason()).isNull();
        }, transport -> {
            assertThat(transport.getClaim()).contains("2,000", "交通补贴");
            assertThat(transport.getStatus()).isEqualTo(ClaimStatus.INSUFFICIENT);
            assertThat(transport.getInsufficientReason()).isEqualTo(InsufficientReason.MODEL_UNSUPPORTED);
        });
        assertThat(response.getGuard().getAction()).isEqualTo(expectedAction);
        if (expectedAction == GuardAction.BLOCKED) {
            assertThat(response.getGuard().isAnswerVisible()).isFalse();
            assertThat(report.getAnswer()).isNull();
        } else {
            assertThat(response.getGuard().getDisplayAnswer())
                    .isEqualTo("公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。");
        }
    }

    @Test
    void canteenQuestionSkipsTheModelWhenLuceneFindsNoEvidence() {
        var response = orchestrator.handle(commandFor("SC07"));

        assertThat(response.getEvidence()).isEmpty();
        assertThat(response.getVerification().getVerificationState()).isEqualTo(VerificationState.NO_EVIDENCE);
        assertThat(response.getTiming().getGenerationMs()).isNull();
    }

    private ChatCommand commandFor(String scenarioId) {
        var scenario = scenarios.findById(scenarioId).orElseThrow();
        return ChatCommand.builder().mode(RunMode.MOCK).scenarioId(scenario.getScenarioId())
                .question(scenario.getQuestion()).build();
    }
}
