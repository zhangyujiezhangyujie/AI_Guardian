package com.aiguardian.controller;

import com.aiguardian.domain.model.VerificationFailureReason;
import com.aiguardian.domain.model.VerificationInput;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.stub.StubClaimVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("stub")
class StubChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean StubClaimVerifier verifier;

    @Test
    void annotatedResponse() throws Exception {
        var result = chat("公司员工有哪些福利？")
                .andExpect(jsonPath("$.mode").value("REAL"))
                .andExpect(jsonPath("$.scenarioId").hasJsonPath())
                .andExpect(jsonPath("$.scenarioId").isEmpty())
                .andExpect(jsonPath("$.verification.unsupportedRatio").value(0.5))
                .andExpect(jsonPath("$.verification.claims[0].quoteExists").value(true))
                .andExpect(jsonPath("$.guard.action").value("ANNOTATED"));
        export("annotated", result.andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void noEvidenceResponse() throws Exception {
        var result = chat("公司食堂周末几点开门？")
                .andExpect(jsonPath("$.evidence").isEmpty())
                .andExpect(jsonPath("$.verification.verificationState").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.timing.generationMs").hasJsonPath())
                .andExpect(jsonPath("$.timing.generationMs").isEmpty())
                .andExpect(jsonPath("$.guard.action").value("NO_EVIDENCE"));
        export("no-evidence", result.andReturn().getResponse().getContentAsByteArray());
    }

    @ParameterizedTest
    @CsvSource({"FAILED, NOT_VERIFIED, not-verified", "NO_CLAIMS, NO_CLAIMS, no-claims"})
    void verifierReportResponses(VerificationState state, String action, String fileName) throws Exception {
        doAnswer(call -> {
            VerificationInput input = call.getArgument(0);
            return VerificationReport.builder().requestId(input.getRequestId()).answer(input.getAnswer())
                    .verificationState(state).verifierName("batch").llmCalls(1)
                    .failureReason(state == VerificationState.FAILED ? VerificationFailureReason.INVALID_JSON : null)
                    .build();
        }).when(verifier).verify(any());

        var result = chat("公司员工有哪些福利？")
                .andExpect(jsonPath("$.verification.verificationState").value(state.name()))
                .andExpect(jsonPath("$.guard.action").value(action));
        export(fileName, result.andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void exposesGuardConfiguration() throws Exception {
        mvc.perform(get("/api/guard/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("annotate"))
                .andExpect(jsonPath("$.blockThreshold").value(0.5))
                .andExpect(jsonPath("$.strictForToolCall").value(true))
                .andExpect(jsonPath("$.availableStrategies[0]").value("annotate"))
                .andExpect(jsonPath("$.availableStrategies[1]").value("block"));
    }

    private org.springframework.test.web.servlet.ResultActions chat(String question) throws Exception {
        return mvc.perform(post("/api/chat").contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("question", question).toString()))
                .andExpect(status().isOk());
    }

    private void export(String name, byte[] response) throws Exception {
        if (Boolean.getBoolean("exportChatExamples")) {
            Path examples = Files.createDirectories(Path.of("target", "chat-examples"));
            Files.writeString(examples.resolve(name + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(response)) + "\n");
        }
    }
}
