package com.aiguardian.application;

import com.aiguardian.audit.AuditService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.guard.AnnotateOutputGuard;
import com.aiguardian.domain.guard.BlockOutputGuard;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.VerificationFailureReason;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.verification.CitationValidator;
import com.aiguardian.domain.verification.ClaimVerifier;
import com.aiguardian.infrastructure.llm.LLMClient;
import com.aiguardian.infrastructure.llm.LLMException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorImplTest {
    @Mock EvidenceRetriever retriever;
    @Mock LLMClient llm;
    @Mock ClaimVerifier verifier;
    @Mock CitationValidator citationValidator;
    @Mock AuditService audit;

    private ChatOrchestrator orchestrator;
    private final ChatCommand command = ChatCommand.builder().mode(RunMode.REAL).question("年假？").build();
    private final List<Evidence> evidence = List.of(Evidence.builder().evidenceId("doc001_chunk03")
            .documentTitle("员工手册").content("年假为15天。").build());

    @BeforeEach
    void setUp() {
        when(verifier.name()).thenReturn("batch");
        var annotate = new AnnotateOutputGuard();
        orchestrator = new ChatOrchestratorImpl(new AiGuardianProperties(), retriever, llm, List.of(verifier),
                citationValidator, new OutputGuardRegistry(List.of(annotate, new BlockOutputGuard(annotate))),
                audit, new ObjectMapper());
    }

    @Test
    void emptyRetrievalSkipsModelCalls() {
        when(retriever.retrieve("年假？", 3)).thenReturn(List.of());

        var response = orchestrator.handle(command);

        assertThat(response.getVerification().getVerificationState()).isEqualTo(VerificationState.NO_EVIDENCE);
        assertThat(response.getTiming().getGenerationMs()).isNull();
        assertThat(response.getTiming().getVerificationMs()).isNull();
        verifyNoInteractions(llm, citationValidator);
    }

    @Test
    void generationErrorSavesRequestAndExposes502() {
        when(retriever.retrieve("年假？", 3)).thenReturn(evidence);
        when(llm.generate(any())).thenThrow(new LLMException(LLMException.Type.API_ERROR));

        var failure = catchThrowableOfType(() -> orchestrator.handle(command), BusinessException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.LLM_GENERATION_FAILED);
        assertThat(failure.getErrorCode().getHttpStatus().value()).isEqualTo(502);
        RequestRecord record = savedRecord();
        assertThat(record.getRequestId()).isEqualTo(failure.getRequestId());
        assertThat(record.getErrorCode()).isEqualTo("LLM_GENERATION_FAILED");
        assertThat(record.getRawAnswer()).isNull();
        assertThat(record.getReport()).isNull();
        assertThat(record.getGuard()).isNull();
        var events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.times(4)).record(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.REQUEST_RECEIVED, AuditEventType.RETRIEVAL_DONE, AuditEventType.GENERATION_FAILED,
                AuditEventType.REQUEST_FAILED);
    }

    @Test
    void failedVerificationSkipsCitationAndHidesAnswerInBlockMode() {
        command.setGuardStrategyOverride("block");
        when(retriever.retrieve("年假？", 3)).thenReturn(evidence);
        when(llm.generate(any())).thenReturn("未经核验");
        when(verifier.verify(any())).thenAnswer(call -> VerificationReport.builder().answer("未经核验")
                .verificationState(VerificationState.FAILED).failureReason(VerificationFailureReason.LLM_TIMEOUT)
                .verifierName("batch").llmCalls(1).build());

        var response = orchestrator.handle(command);

        assertThat(response.getGuard().getAction()).isEqualTo(GuardAction.NOT_VERIFIED);
        assertThat(response.getVerification().getAnswer()).isNull();
        assertThat(savedRecord().getRawAnswer()).isEqualTo("未经核验");
        verifyNoInteractions(citationValidator);
    }

    @Test
    void blockedResponseRedactsOnlyTheCopy() {
        command.setGuardStrategyOverride("block");
        command.setBlockThresholdOverride(0.3);
        when(retriever.retrieve("年假？", 3)).thenReturn(evidence);
        when(llm.generate(any())).thenReturn("交通补贴");
        var claim = ClaimVerification.builder().claim("补贴").originalText("交通补贴").startIndex(0).endIndex(4)
                .status(ClaimStatus.INSUFFICIENT).build();
        var report = VerificationReport.builder().verificationState(VerificationState.COMPLETED)
                .answer("交通补贴").verifierName("batch").claims(List.of(claim)).build();
        report.recalculate();
        when(verifier.verify(any())).thenReturn(report);
        when(citationValidator.validate(eq(report), eq(evidence))).thenReturn(report);

        var response = orchestrator.handle(command);

        assertThat(response.getGuard().getAction()).isEqualTo(GuardAction.BLOCKED);
        assertThat(response.getVerification().getAnswer()).isNull();
        assertThat(response.getVerification().getClaims().get(0).getOriginalText()).isNull();
        assertThat(response.getVerification().getClaims().get(0).getStartIndex()).isNull();
        assertThat(response.getVerification().getClaims().get(0).getEndIndex()).isNull();
        assertThat(response.getVerification().getClaims().get(0).getClaim()).isEqualTo("补贴");
        var record = savedRecord();
        assertThat(record.getRawAnswer()).isEqualTo("交通补贴");
        assertThat(record.getReport().getAnswer()).isEqualTo("交通补贴");
        assertThat(record.getReport().getClaims().get(0).getOriginalText()).isEqualTo("交通补贴");
        assertThat(record.getReport().getClaims().get(0).getEndIndex()).isEqualTo(4);
    }

    @Test
    void auditErrorsDoNotChangeTheResponse() {
        when(retriever.retrieve("年假？", 3)).thenReturn(List.of());
        doThrow(new IllegalStateException()).when(audit).record(any());
        doThrow(new IllegalStateException()).when(audit).saveRequestRecord(any());

        assertThat(orchestrator.handle(command).getGuard().getAction()).isEqualTo(GuardAction.NO_EVIDENCE);
    }

    @Test
    void noneOverrideSkipsVerificationForRealPerformanceRuns() {
        command.setVerifierOverride("none");
        when(retriever.retrieve("年假？", 3)).thenReturn(evidence);
        when(llm.generate(any())).thenReturn("年假为15天。");

        var response = orchestrator.handle(command);

        assertThat(response.getGuard().getAction()).isEqualTo(GuardAction.NO_CLAIMS);
        assertThat(response.getVerification().getVerifierName()).isEqualTo("none");
        assertThat(response.getTiming().getVerificationMs()).isNull();
        verifyNoInteractions(citationValidator);
        verify(verifier, org.mockito.Mockito.never()).verify(any());
    }

    private RequestRecord savedRecord() {
        var saved = ArgumentCaptor.forClass(RequestRecord.class);
        verify(audit).saveRequestRecord(saved.capture());
        return saved.getValue();
    }
}
