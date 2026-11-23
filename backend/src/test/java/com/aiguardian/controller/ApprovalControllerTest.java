package com.aiguardian.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiguardian.application.ApprovalService;
import com.aiguardian.application.ToolCallService;
import com.aiguardian.application.ToolExecutor;
import com.aiguardian.audit.AuditService;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.tool.InMemoryApprovalRepository;
import com.aiguardian.domain.tool.InMemoryBusinessRecordRepository;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApprovalControllerTest {

    private MockMvc mockMvc;

    private ToolCallService tools;

    @BeforeEach
    void setUp() {
        AiGuardianProperties properties = new AiGuardianProperties();
        InMemoryBusinessRecordRepository records = new InMemoryBusinessRecordRepository();
        InMemoryApprovalRepository approvals = new InMemoryApprovalRepository();
        ToolCatalog catalog = new ToolCatalog();
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
        ToolGateway gateway = new ToolGateway(properties, catalog, records);
        ToolExecutor executor = new ToolExecutor(catalog, records, retriever);
        tools = new ToolCallService(gateway, executor, approvals, requestId -> Optional.empty(), audit, properties);
        ApprovalService service = new ApprovalService(approvals, gateway, executor, requestId -> Optional.empty(), audit, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ApprovalController(service, properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String newApproval(String requester) {
        return tools.execute(new ToolCallRequest("delete_record", Map.of("recordId", 1001), null), requester).getApprovalId();
    }

    @Test
    void listAndGet() throws Exception {
        String id = newApproval("admin");
        mockMvc.perform(get("/api/approvals").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalId").value(id))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].requester").value("admin"))
                .andExpect(jsonPath("$[0].arguments.recordId").value(1001))
                .andExpect(jsonPath("$[0].result").doesNotExist());
        mockMvc.perform(get("/api/approvals/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.toolName").value("delete_record"));
        mockMvc.perform(get("/api/approvals").param("status", "NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/approvals/apr_000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("审批单不存在。"));
    }

    @Test
    void approveExecutesAndReturnsRecordSnapshot() throws Exception {
        String id = newApproval("admin");
        mockMvc.perform(post("/api/approvals/" + id + "/approve").header("X-Demo-User", "manager")
                        .contentType(APPLICATION_JSON).content("{\"comment\":\" 同意 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.approver").value("manager"))
                .andExpect(jsonPath("$.comment").value("同意"))
                .andExpect(jsonPath("$.result.recordId").value(1001))
                .andExpect(jsonPath("$.record.deleted").value(true));
    }

    @Test
    void selfApprovalIs403AndRejectWorksWithoutBody() throws Exception {
        String id = newApproval("manager");
        mockMvc.perform(post("/api/approvals/" + id + "/approve").header("X-Demo-User", "manager"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("无权处理该审批单：申请人不能审批自己的申请。"));

        String other = newApproval("admin");
        mockMvc.perform(post("/api/approvals/" + other + "/reject").header("X-Demo-User", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.record.deleted").value(false));
        mockMvc.perform(post("/api/approvals/" + other + "/approve").header("X-Demo-User", "manager"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_NOT_PENDING"));
    }

    @Test
    void invalidHeaderOrBodyIs400() throws Exception {
        String id = newApproval("admin");
        mockMvc.perform(post("/api/approvals/" + id + "/approve")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/approvals/" + id + "/approve").header("X-Demo-User", "guest")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/approvals/" + id + "/approve").header("X-Demo-User", "manager")
                .contentType(APPLICATION_JSON).content("{\"comment\":\"" + "字".repeat(201) + "\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/approvals/" + id + "/approve").header("X-Demo-User", "manager")
                .contentType(APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isBadRequest());
    }
}
