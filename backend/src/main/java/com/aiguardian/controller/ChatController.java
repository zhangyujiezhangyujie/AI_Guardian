package com.aiguardian.controller;

import com.aiguardian.application.ChatCommand;
import com.aiguardian.application.ChatOrchestrator;
import com.aiguardian.common.BusinessException;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.RunMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private static final Pattern SESSION_ID = Pattern.compile("^ses_[0-9a-f]{12}$");
    private final ChatOrchestrator orchestrator;

    @PostMapping(path = "/api/chat", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody JsonNode body) {
        if (!body.isObject()) {
            throw BusinessException.invalidRequest("请求体必须为 JSON 对象。");
        }
        int allowedFields = (body.has("question") ? 1 : 0) + (body.has("sessionId") ? 1 : 0);
        if (body.size() > allowedFields) {
            throw BusinessException.invalidRequest("只允许 question 和 sessionId 字段。");
        }
        String question = body.path("question").isTextual() ? body.get("question").textValue().strip() : "";
        if (question.isEmpty() || question.length() > 500) {
            throw BusinessException.invalidRequest("question 去除首尾空白后的长度必须为 1 到 500。");
        }
        JsonNode session = body.get("sessionId");
        String sessionId = null;
        if (session != null && !session.isNull()) {
            if (!session.isTextual() || !SESSION_ID.matcher(session.textValue()).matches()) {
                throw BusinessException.invalidRequest("sessionId 格式无效。");
            }
            sessionId = session.textValue();
        }
        return orchestrator.handle(ChatCommand.builder().question(question).sessionId(sessionId)
                .mode(RunMode.REAL).build());
    }
}
