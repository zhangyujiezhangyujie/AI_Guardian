package com.aiguardian.controller;

import com.aiguardian.application.ChatOrchestrator;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.RunMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {
    private final ChatOrchestrator orchestrator = mock(ChatOrchestrator.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(orchestrator))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"question\":\"   \"}", "{\"question\":null}", "{\"question\":123}",
            "{\"question\":\"年假\",\"sessionId\":\"ses_bad\"}", "{\"question\":\"年假\",\"mode\":\"MOCK\"}", "{"})
    void rejectsInvalidInput(String body) throws Exception {
        expectInvalid(body);
    }

    @Test
    void rejectsQuestionOver500Characters() throws Exception {
        expectInvalid("{\"question\":\"" + "问".repeat(501) + "\"}");
    }

    @Test
    void trimsQuestionAndForwardsRealMode() throws Exception {
        var realCommand = argThat((com.aiguardian.application.ChatCommand cmd) -> "年假".equals(cmd.getQuestion())
                && cmd.getMode() == RunMode.REAL && "ses_0123456789ab".equals(cmd.getSessionId()));
        when(orchestrator.handle(realCommand)).thenReturn(ChatResponse.builder().mode(RunMode.REAL).build());

        mvc.perform(post("/api/chat").contentType(APPLICATION_JSON)
                        .content("{\"question\":\" 年假 \",\"sessionId\":\"ses_0123456789ab\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("REAL"));
        verify(orchestrator).handle(argThat(cmd -> "年假".equals(cmd.getQuestion()) && cmd.getMode() == RunMode.REAL));
    }

    private void expectInvalid(String body) throws Exception {
        mvc.perform(post("/api/chat").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").hasJsonPath()).andExpect(jsonPath("$.requestId").isEmpty());
        verifyNoInteractions(orchestrator);
    }
}
