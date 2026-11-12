package com.aiguardian.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"aiguardian.output-guard.strategy=block", "aiguardian.output-guard.block-threshold=0.3"})
@AutoConfigureMockMvc
@ActiveProfiles("stub")
class BlockedChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void hidesRawAnswerAndClaimOffsets() throws Exception {
        var response = mvc.perform(post("/api/chat").contentType(APPLICATION_JSON)
                        .content("{\"question\":\"公司员工有哪些福利？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guard.action").value("BLOCKED"))
                .andExpect(jsonPath("$.guard.answerVisible").value(false))
                .andExpect(jsonPath("$.guard.displayAnswer").hasJsonPath())
                .andExpect(jsonPath("$.guard.displayAnswer").isEmpty())
                .andExpect(jsonPath("$.verification.answer").hasJsonPath())
                .andExpect(jsonPath("$.verification.answer").isEmpty())
                .andExpect(jsonPath("$.verification.claims[0].originalText").isEmpty())
                .andExpect(jsonPath("$.verification.claims[0].startIndex").isEmpty())
                .andExpect(jsonPath("$.verification.claims[0].endIndex").isEmpty())
                .andReturn().getResponse();
        if (Boolean.getBoolean("exportChatExamples")) {
            Path examples = Files.createDirectories(Path.of("target", "chat-examples"));
            Files.writeString(examples.resolve("blocked.json"), json.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(json.readTree(response.getContentAsByteArray())) + "\n");
        }
    }
}
