package com.aiguardian.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiguardian.application.ChatOrchestrator;
import com.aiguardian.application.ChatOrchestratorImpl;
import com.aiguardian.application.ToolCallService;
import com.aiguardian.application.ToolExecutor;
import com.aiguardian.audit.AuditService;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.demo.DefaultScenarioExpectationChecker;
import com.aiguardian.demo.JsonScenarioRepository;
import com.aiguardian.demo.ScenarioExpectationChecker;
import com.aiguardian.domain.guard.AnnotateOutputGuard;
import com.aiguardian.domain.guard.BlockOutputGuard;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.tool.InMemoryApprovalRepository;
import com.aiguardian.domain.tool.InMemoryBusinessRecordRepository;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import com.aiguardian.domain.verification.ExactQuoteCitationValidator;
import com.aiguardian.domain.verification.LLMBatchClaimVerifier;
import com.aiguardian.infrastructure.llm.MockLLMClient;
import com.aiguardian.infrastructure.lucene.Chunker;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseIndex;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseLoader;
import com.aiguardian.infrastructure.lucene.LuceneEvidenceRetriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DemoControllerTest {

    private KnowledgeBaseIndex index;
    private JsonScenarioRepository scenarios;
    private ScenarioExpectationChecker checker;
    private OutputGuardRegistry guards;
    private MockMvc mockMvc;
    private AiGuardianProperties properties;
    private InMemoryBusinessRecordRepository records;
    private ToolCallService tools;

    @BeforeEach
    void setUp() {
        properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        properties.getRetrieval().setMinScore(1.0);
        properties.getDemo().setTestCasesDir("../test-cases");

        index = new KnowledgeBaseIndex(properties, new KnowledgeBaseLoader(properties), new Chunker(properties));
        index.build();
        scenarios = new JsonScenarioRepository(properties, new ObjectMapper());
        scenarios.load();

        ObjectMapper json = new ObjectMapper();
        MockLLMClient mock = new MockLLMClient(scenarios);
        AnnotateOutputGuard annotate = new AnnotateOutputGuard();
        guards = new OutputGuardRegistry(List.of(annotate, new BlockOutputGuard(annotate)));
        checker = new DefaultScenarioExpectationChecker();
        ChatOrchestrator orchestrator = new ChatOrchestratorImpl(properties, new LuceneEvidenceRetriever(index), mock,
                List.of(new LLMBatchClaimVerifier(mock, json, properties)),
                new ExactQuoteCitationValidator(properties), guards, noopAudit(), json);
        records = new InMemoryBusinessRecordRepository();
        ToolCatalog catalog = new ToolCatalog();
        tools = new ToolCallService(new ToolGateway(properties, catalog, records),
                new ToolExecutor(catalog, records, new LuceneEvidenceRetriever(index)),
                new InMemoryApprovalRepository(), requestId -> Optional.empty(), noopAudit(), properties);
        mockMvc = mockMvcWith(orchestrator);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void listsScenariosWithoutMockOrExpected() throws Exception {
        mockMvc.perform(get("/api/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].scenarioId").value("SC01"))
                .andExpect(jsonPath("$[0].name").value("完整证据支持"))
                .andExpect(jsonPath("$[0].question").value("公司正式员工一年有多少天年假？"))
                .andExpect(jsonPath("$[0].priority").value("P0"))
                .andExpect(jsonPath("$[0].supportsReal").value(true))
                .andExpect(jsonPath("$[0].recommendedStrategies[0]").value("annotate"))
                .andExpect(jsonPath("$[0].mock").doesNotExist())
                .andExpect(jsonPath("$[0].expected").doesNotExist());
    }

    @Test
    void mockRunComparesDeclaredExpectations() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"MOCK"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check.checked").value(true))
                .andExpect(jsonPath("$.check.passed").value(true))
                .andExpect(jsonPath("$.check.mismatches").isEmpty())
                .andExpect(jsonPath("$.response.verification.verificationState").value("COMPLETED"))
                .andExpect(jsonPath("$.response.guard.action").value("ANNOTATED"));
    }

    @Test
    void blockStrategyBlocksScenarioWithTooManyUnsupportedClaims() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC03","mode":"MOCK","guardStrategy":"block"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check.checked").value(true))
                .andExpect(jsonPath("$.check.passed").value(true))
                .andExpect(jsonPath("$.response.guard.action").value("BLOCKED"));
    }

    @Test
    void generationFailureStillReturnsHttp200WithNullResponse() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC09","mode":"MOCK"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(nullValue()))
                .andExpect(jsonPath("$.check.checked").value(true))
                .andExpect(jsonPath("$.check.passed").value(true));
    }

    @Test
    void sc06PassesProposedToolCallToGatewayWhichDeniesIt() throws Exception {
        for (String strategy : List.of("annotate", "block")) {
            mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content(
                            "{\"scenarioId\":\"SC06\",\"mode\":\"MOCK\",\"guardStrategy\":\"" + strategy + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.check.passed").value(true))
                    .andExpect(jsonPath("$.check.mismatches").isEmpty())
                    .andExpect(jsonPath("$.response.guard.action").value("block".equals(strategy) ? "BLOCKED" : "ANNOTATED"))
                    .andExpect(jsonPath("$.toolCall.user").value("user"))
                    .andExpect(jsonPath("$.toolCall.toolName").value("delete_record"))
                    .andExpect(jsonPath("$.toolCall.decision").value("DENY"))
                    .andExpect(jsonPath("$.toolCall.denyReason").value("PERMISSION_DENIED"))
                    .andExpect(jsonPath("$.toolCall.executed").value(false))
                    .andExpect(jsonPath("$.toolCall.record.recordId").value(1001))
                    .andExpect(jsonPath("$.toolCall.record.deleted").value(false));
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, records.writes());
    }

    @Test
    void sc06ToolCallIsBoundToTheRunRequestId() throws Exception {
        String body = mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON)
                        .content("{\"scenarioId\":\"SC06\",\"mode\":\"MOCK\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tree = new ObjectMapper().readTree(body);
        org.junit.jupiter.api.Assertions.assertEquals(
                tree.at("/response/requestId").asText(), tree.at("/toolCall/basedOnRequestId").asText());
    }

    @Test
    void misconfiguredPermissionIsStillCaughtByUnverifiedBasisCheck() throws Exception {
        // 纵深防御：即使误把删除权限给了普通员工、去掉审批，回答中的结论缺乏证据，第 5 步仍会拒绝
        properties.getToolGateway().setRoles(Map.of("user", List.of("delete_record")));
        properties.getToolGateway().setApprovalRequired(List.of());
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON)
                        .content("{\"scenarioId\":\"SC06\",\"mode\":\"MOCK\",\"guardStrategy\":\"annotate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCall.decision").value("DENY"))
                .andExpect(jsonPath("$.toolCall.denyReason").value("UNVERIFIED_BASIS"))
                .andExpect(jsonPath("$.toolCall.record.deleted").value(false));
        org.junit.jupiter.api.Assertions.assertEquals(0, records.writes());
    }

    @Test
    void wrongToolDecisionFailsTheExpectationCheck() throws Exception {
        // 人为关闭全部防护：普通员工可直接删除、无需审批、不检查依据，决策变为 ALLOW，与 SC06 期望不符
        properties.getToolGateway().setRoles(Map.of("user", List.of("delete_record")));
        properties.getToolGateway().setApprovalRequired(List.of());
        properties.getOutputGuard().setStrictForToolCall(false);
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON)
                        .content("{\"scenarioId\":\"SC06\",\"mode\":\"MOCK\",\"guardStrategy\":\"annotate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCall.decision").value("ALLOW"))
                .andExpect(jsonPath("$.check.passed").value(false))
                .andExpect(jsonPath("$.check.mismatches[0]").value("toolCall.decision 期望 DENY，实际 ALLOW"));
    }

    @Test
    void scenariosWithoutToolCallReturnNullToolCall() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON)
                        .content("{\"scenarioId\":\"SC01\",\"mode\":\"MOCK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCall").value(nullValue()));
    }

    @Test
    void unknownScenarioReturns404() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC99","mode":"MOCK"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SCENARIO_NOT_FOUND"));
    }

    @Test
    void blankScenarioIdReturns400() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":" ","mode":"MOCK"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void verifierIsRejectedInMockMode() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"MOCK","verifier":"batch"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void unknownVerifierIsRejected() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"REAL","verifier":"nope"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void realModeIsRejectedWhenScenarioDoesNotSupportIt() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC05","mode":"REAL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void unknownGuardStrategyIsRejected() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"MOCK","guardStrategy":"nope"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void outOfRangeBlockThresholdIsRejected() throws Exception {
        mockMvc.perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"MOCK","blockThreshold":1.5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void realModeSkipsExpectationCheck() throws Exception {
        ChatOrchestrator fake = command -> ChatResponse.builder()
                .requestId("req_fake").question(command.getQuestion()).mode(command.getMode())
                .evidence(List.of()).createdAt("2026-11-19T20:00:00+08:00").build();

        mockMvcWith(fake).perform(post("/api/demo/run").contentType(APPLICATION_JSON).content("""
                        {"scenarioId":"SC01","mode":"REAL","verifier":"none"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check.checked").value(false))
                .andExpect(jsonPath("$.check.passed").value(nullValue()))
                .andExpect(jsonPath("$.check.mismatches").isEmpty());
    }

    private MockMvc mockMvcWith(ChatOrchestrator orchestrator) {
        return MockMvcBuilders.standaloneSetup(new DemoController(scenarios, orchestrator, checker, guards, tools))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static AuditService noopAudit() {
        return new AuditService() {
            @Override
            public void record(AuditEvent event) {
            }

            @Override
            public void saveRequestRecord(RequestRecord record) {
            }
        };
    }
}
