package com.aiguardian.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.aiguardian.application.ToolCallService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 工具调用安全网关接口（契约 7.10）。
 * 请求格式错误返回 400；格式正确时无论放行还是拒绝都返回 200 + ToolCallResult。
 */
@RestController
@Profile("!stub")
public class ToolController {

    static final String USER_HEADER = "X-Demo-User";

    private static final Set<String> FIELDS = Set.of("toolName", "arguments", "basedOnRequestId");

    private static final Pattern REQUEST_ID = Pattern.compile("^req_[0-9a-f]{12}$");

    private final ToolCallService tools;

    private final ObjectMapper json;

    private final AiGuardianProperties properties;

    public ToolController(ToolCallService tools, ObjectMapper json, AiGuardianProperties properties) {
        this.tools = tools;
        this.json = json;
        this.properties = properties;
    }

    @PostMapping(path = "/api/tools/execute", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ToolCallResult execute(@RequestHeader(value = USER_HEADER, required = false) String user,
                                  @RequestBody JsonNode body) {
        // 演示用户即 tool-gateway.roles 的键（默认 user、manager、admin），仅用于演示，不是真实身份认证
        if (user == null || !properties.getToolGateway().getRoles().containsKey(user)) {
            throw BusinessException.invalidRequest("请求头 X-Demo-User 必须为 " + String.join("、",
                    properties.getToolGateway().getRoles().keySet().stream().sorted().toList()) + " 之一。");
        }
        return tools.execute(parse(body), user);
    }

    private ToolCallRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw BusinessException.invalidRequest("请求体必须为 JSON 对象。");
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) {
                throw BusinessException.invalidRequest("只允许 toolName、arguments、basedOnRequestId 字段。");
            }
        });

        JsonNode toolName = body.get("toolName");
        if (toolName == null || !toolName.isTextual() || toolName.textValue().isBlank()) {
            throw BusinessException.invalidRequest("toolName 必填。");
        }

        JsonNode arguments = body.get("arguments");
        if (arguments == null || !arguments.isObject()) {
            throw BusinessException.invalidRequest("arguments 必须为 JSON 对象。");
        }

        JsonNode basedOn = body.get("basedOnRequestId");
        String basedOnRequestId = null;
        if (basedOn != null && !basedOn.isNull()) {
            if (!basedOn.isTextual() || !REQUEST_ID.matcher(basedOn.textValue()).matches()) {
                throw BusinessException.invalidRequest("basedOnRequestId 格式无效。");
            }
            basedOnRequestId = basedOn.textValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> argumentMap = json.convertValue(arguments, Map.class);
        return ToolCallRequest.builder()
                .toolName(toolName.textValue())
                .arguments(argumentMap)
                .basedOnRequestId(basedOnRequestId)
                .build();
    }
}
