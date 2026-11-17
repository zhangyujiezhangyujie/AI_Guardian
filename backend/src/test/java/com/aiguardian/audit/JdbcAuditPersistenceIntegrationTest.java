package com.aiguardian.audit;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.Timing;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.common.ShanghaiTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit MySQL-backed persistence and audit endpoint check; skipped by default. */
@SpringBootTest(properties = {
        "aiguardian.llm.base-url=http://127.0.0.1",
        "aiguardian.llm.api-key=unused-for-this-test",
        "aiguardian.llm.model=unused-for-this-test"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "runMysqlAuditIntegration", matches = "true")
class JdbcAuditPersistenceIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MockMvc mvc;

    @Test
    void persistsRequestAndEventsThenServesListAndDetail() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String requestId = "req_it_" + suffix;
        String sessionId = "ses_it_" + suffix;
        var createdAt = ShanghaiTime.now();
        Evidence evidence = Evidence.builder()
                .evidenceId("doc001_chunk01")
                .documentId("doc001")
                .documentTitle("测试员工手册")
                .section("年假制度")
                .content("正式员工每年享有15天带薪年假。")
                .score(1.0)
                .rank(1)
                .build();
        ClaimVerification claim = ClaimVerification.builder()
                .claimId("c1")
                .claim("正式员工每年享有15天带薪年假")
                .originalText("正式员工每年享有15天带薪年假")
                .modelSupported(true)
                .status(ClaimStatus.SUPPORTED)
                .evidenceId(evidence.getEvidenceId())
                .quote("正式员工每年享有15天带薪年假")
                .quoteExists(true)
                .build();
        VerificationReport report = VerificationReport.builder()
                .requestId(requestId)
                .verificationState(VerificationState.COMPLETED)
                .answer(claim.getClaim())
                .claims(List.of(claim))
                .verifierName("batch")
                .llmCalls(1)
                .build();
        report.recalculate();
        GuardResult guard = GuardResult.builder()
                .strategy("annotate")
                .blockThreshold(0.5)
                .action(GuardAction.ANNOTATED)
                .answerVisible(true)
                .displayAnswer(report.getAnswer())
                .unsupportedRatio(report.getUnsupportedRatio())
                .build();
        RequestRecord record = RequestRecord.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .mode(RunMode.MOCK)
                .scenarioId("SC01")
                .question("公司正式员工一年有多少天年假？")
                .rawAnswer(report.getAnswer())
                .evidence(List.of(evidence))
                .report(report)
                .guard(guard)
                .timing(new Timing(4, 3L, 5L, 15))
                .createdAt(createdAt)
                .build();

        try {
            auditService.saveRequestRecord(record);
            auditService.record(AuditEvent.builder()
                    .requestId(requestId)
                    .eventType(AuditEventType.REQUEST_RECEIVED)
                    .success(true)
                    .detail(Map.of("mode", "MOCK", "scenarioId", "SC01", "questionLength", 17))
                    .createdAt(createdAt)
                    .build());

            mvc.perform(get("/api/audit")
                            .param("requestId", requestId)
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.items[0].requestId").value(requestId))
                    .andExpect(jsonPath("$.items[0].supportedClaims").value(1));

            mvc.perform(get("/api/audit/{requestId}", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(sessionId))
                    .andExpect(jsonPath("$.rawAnswer").value(report.getAnswer()))
                    .andExpect(jsonPath("$.evidence[0].evidenceId").value(evidence.getEvidenceId()))
                    .andExpect(jsonPath("$.verification.claims[0].status").value("SUPPORTED"))
                    .andExpect(jsonPath("$.guard.strategy").value("annotate"))
                    .andExpect(jsonPath("$.events[0].eventType").value("REQUEST_RECEIVED"));
        } finally {
            jdbc.update("DELETE FROM audit_event WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM claim_record WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM chat_message WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM verification_record WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM chat_session WHERE id = ?", sessionId);
        }
    }
}
