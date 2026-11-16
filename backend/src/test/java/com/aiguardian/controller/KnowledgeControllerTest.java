package com.aiguardian.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.infrastructure.lucene.Chunker;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseIndex;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeControllerTest {

    private KnowledgeBaseIndex index;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        index = new KnowledgeBaseIndex(properties, new KnowledgeBaseLoader(properties), new Chunker(properties));
        index.build();
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(index))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void listsDocumentsSortedById() throws Exception {
        mockMvc.perform(get("/api/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].documentId").value("doc001"))
                .andExpect(jsonPath("$[0].title").value("员工手册"))
                .andExpect(jsonPath("$[0].fileName").value("doc001_员工手册.md"))
                .andExpect(jsonPath("$[0].chunkCount").value(4))
                .andExpect(jsonPath("$[7].documentId").value("doc008"));
    }

    @Test
    void returnsDocumentDetailWithContentAndChunks() throws Exception {
        mockMvc.perform(get("/api/knowledge/documents/doc001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc001"))
                .andExpect(jsonPath("$.title").value("员工手册"))
                .andExpect(jsonPath("$.content", startsWith("# 员工手册")))
                .andExpect(jsonPath("$.chunks[0].evidenceId").value("doc001_chunk01"))
                .andExpect(jsonPath("$.chunks[0].section").value("总则与入职管理"))
                .andExpect(jsonPath("$.chunks[0].content").isNotEmpty());
    }

    @Test
    void missingDocumentReturns404() throws Exception {
        mockMvc.perform(get("/api/knowledge/documents/doc999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void returnsEvidenceWithZeroScoreAndRank() throws Exception {
        mockMvc.perform(get("/api/knowledge/evidence/doc001_chunk02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value("doc001_chunk02"))
                .andExpect(jsonPath("$.documentId").value("doc001"))
                .andExpect(jsonPath("$.section").value("年假制度"))
                .andExpect(jsonPath("$.score").value(0.0))
                .andExpect(jsonPath("$.rank").value(0))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void missingEvidenceReturns404() throws Exception {
        mockMvc.perform(get("/api/knowledge/evidence/doc999_chunk01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVIDENCE_NOT_FOUND"));
    }
}
