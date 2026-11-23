package com.aiguardian.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiguardian.application.ToolCallService;
import com.aiguardian.audit.AuditService;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.tool.InMemoryBusinessRecordRepository;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolControllerTest {

    private MockMvc mockMvc;

    private InMemoryBusinessRecordRepository records;

    @BeforeEach
    void setUp() {
        AiGuardianProperties properties = new AiGuardianProperties();
        records = new InMemoryBusinessRecordRepository();
        ToolCatalog catalog = new ToolCatalog();
        VerificationReport insufficient = VerificationReport.builder()
                .requestId("req_bbbbbbbbbbbb")
                .verificationState(VerificationState.COMPLETED)
                .claims(List.of(ClaimVerification.builder().claimId("c1").claim("x").status(ClaimStatus.INSUFFICIENT).build()))
                .build();
        insufficient.recalculate();
        Map<String, VerificationReport> bases = Map.of("req_bbbbbbbbbbbb", insufficient);
        EvidenceRetriever retriever = new EvidenceRetriever() {
            @Override
            public List<Evidence> retrieve(String query, int topK) {
                return List.of();
            }

            @Override
            public Optional<Evidence> findById(String evidenceId) {
                return Optional.empty();
            }
        };
        AuditService audit = new AuditService() {
            @Override
            public void record(AuditEvent event) {
            }

            @Override
            public void saveRequestRecord(RequestRecord record) {
            }
        };
        ToolCallService service = new ToolCallService(new ToolGateway(properties, catalog, records),
                new com.aiguardian.application.ToolExecutor(catalog, records, retriever),
                new com.aiguardian.domain.tool.InMemoryApprovalRepository(),
                requestId -> Optional.ofNullable(bases.get(requestId)), audit, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolController(service, new ObjectMapper(), properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions call(String user, String body) throws Exception {
        var request = post("/api/tools/execute").contentType(APPLICATION_JSON).content(body);
        if (user != null) {
            request.header("X-Demo-User", user);
        }
        return mockMvc.perform(request);
    }

    @Test
    void deniedCallReturns200WithDecision() throws Exception {
        call("user", "{\"toolName\":\"delete_record\",\"arguments\":{\"recordId\":1001}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.denyReason").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.user").value("user"))
                .andExpect(jsonPath("$.arguments.recordId").value(1001))
                .andExpect(jsonPath("$.executed").value(false))
                .andExpect(jsonPath("$.record.deleted").value(false));
    }

    @Test
    void allowedUpdateExecutes() throws Exception {
        call("manager", "{\"toolName\":\"update_record\",\"arguments\":{\"recordId\":1002,\"status\":\"CLOSED\"},\"basedOnRequestId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.executed").value(true))
                .andExpect(jsonPath("$.result.statusAfter").value("CLOSED"))
                .andExpect(jsonPath("$.record.status").value("CLOSED"));
    }

    @Test
    void basisLookupGoesThroughTheController() throws Exception {
        call("manager", "{\"toolName\":\"update_record\",\"arguments\":{\"recordId\":1004,\"status\":\"OPEN\"},\"basedOnRequestId\":\"req_bbbbbbbbbbbb\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denyReason").value("UNVERIFIED_BASIS"));
        call("manager", "{\"toolName\":\"update_record\",\"arguments\":{\"recordId\":1004,\"status\":\"OPEN\"},\"basedOnRequestId\":\"req_cccccccccccc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denyReason").value("BASIS_NOT_FOUND"));
    }

    @Test
    void missingOrUnknownUserHeaderIs400() throws Exception {
        String body = "{\"toolName\":\"search_document\",\"arguments\":{\"keyword\":\"年假\"}}";
        call(null, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        call("guest", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        // v1.4：admin 是合法的演示用户
        call("admin", body).andExpect(status().isOk());
    }

    @Test
    void malformedBodiesAre400() throws Exception {
        for (String body : List.of(
                "[]",
                "{\"arguments\":{}}",
                "{\"toolName\":\" \",\"arguments\":{}}",
                "{\"toolName\":\"search_document\"}",
                "{\"toolName\":\"search_document\",\"arguments\":[]}",
                "{\"toolName\":\"search_document\",\"arguments\":{},\"basedOnRequestId\":\"abc\"}",
                "{\"toolName\":\"search_document\",\"arguments\":{},\"extra\":1}")) {
            call("manager", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Test
    void unknownToolIsADecisionNotAnError() throws Exception {
        call("manager", "{\"toolName\":\"drop_table\",\"arguments\":{}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.denyReason").value("TOOL_NOT_ALLOWED"));
    }
}
