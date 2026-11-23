package com.aiguardian.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.aiguardian.application.ApprovalService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P1 简化审批接口（契约 7.11，v1.4）。 */
@RestController
@RequestMapping("/api/approvals")
@Profile("!stub")
public class ApprovalController {

    private static final int COMMENT_MAX_LENGTH = 200;

    private final ApprovalService approvals;

    private final AiGuardianProperties properties;

    public ApprovalController(ApprovalService approvals, AiGuardianProperties properties) {
        this.approvals = approvals;
        this.properties = properties;
    }

    @GetMapping(produces = APPLICATION_JSON_VALUE)
    public List<ApprovalRequest> list(@RequestParam(required = false) String status) {
        return approvals.list(parseStatus(status));
    }

    @GetMapping(path = "/{approvalId}", produces = APPLICATION_JSON_VALUE)
    public ApprovalRequest get(@PathVariable String approvalId) {
        return approvals.get(approvalId);
    }

    @PostMapping(path = "/{approvalId}/approve", produces = APPLICATION_JSON_VALUE)
    public ApprovalRequest approve(@PathVariable String approvalId,
                                   @RequestHeader(value = ToolController.USER_HEADER, required = false) String user,
                                   @RequestBody(required = false) JsonNode body) {
        return approvals.approve(approvalId, demoUser(user), comment(body));
    }

    @PostMapping(path = "/{approvalId}/reject", produces = APPLICATION_JSON_VALUE)
    public ApprovalRequest reject(@PathVariable String approvalId,
                                  @RequestHeader(value = ToolController.USER_HEADER, required = false) String user,
                                  @RequestBody(required = false) JsonNode body) {
        return approvals.reject(approvalId, demoUser(user), comment(body));
    }

    private String demoUser(String user) {
        if (user == null || !properties.getToolGateway().getRoles().containsKey(user)) {
            throw BusinessException.invalidRequest("请求头 X-Demo-User 必须为 " + String.join("、",
                    properties.getToolGateway().getRoles().keySet().stream().sorted().toList()) + " 之一。");
        }
        return user;
    }

    private static String comment(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return null;
        }
        if (!body.isObject()) {
            throw BusinessException.invalidRequest("请求体必须为 JSON 对象。");
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!"comment".equals(name)) {
                throw BusinessException.invalidRequest("只允许 comment 字段。");
            }
        });
        JsonNode comment = body.get("comment");
        if (comment == null || comment.isNull()) {
            return null;
        }
        if (!comment.isTextual() || comment.textValue().strip().length() > COMMENT_MAX_LENGTH) {
            throw BusinessException.invalidRequest("comment 必须为不超过 200 字的文本。");
        }
        String text = comment.textValue().strip();
        return text.isEmpty() ? null : text;
    }

    private static ApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(status.strip());
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidRequest("status 参数无效。");
        }
    }
}
