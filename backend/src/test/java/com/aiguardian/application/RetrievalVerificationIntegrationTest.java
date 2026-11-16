package com.aiguardian.application;

import com.aiguardian.audit.AuditService;
import com.aiguardian.demo.ScenarioRepository;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.VerificationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class RetrievalVerificationIntegrationTest {
    @Autowired ChatOrchestrator orchestrator;
    @Autowired ScenarioRepository scenarios;
    @MockBean AuditService audit;

    @Test
    void verifiesTheAnnualLeaveCitationAgainstTheLuceneResult() {
        var response = orchestrator.handle(commandFor("SC01"));

        assertThat(response.getVerification().getVerificationState()).isEqualTo(VerificationState.COMPLETED);
        assertThat(response.getVerification().getClaims()).singleElement().satisfies(claim -> {
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUPPORTED);
            assertThat(claim.getQuoteExists()).isTrue();
        });
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
