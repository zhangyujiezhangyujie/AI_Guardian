package com.aiguardian.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不正确：%s"),
    SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "测试场景不存在。"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "文档不存在。"),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "证据片段不存在。"),
    AUDIT_NOT_FOUND(HttpStatus.NOT_FOUND, "审计记录不存在。"),
    LLM_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "大模型生成回答失败，请稍后重试。"),
    RETRIEVAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "知识库检索失败。"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误。"),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "审批单不存在。"),
    APPROVAL_FORBIDDEN(HttpStatus.FORBIDDEN, "无权处理该审批单：%s"),
    APPROVAL_NOT_PENDING(HttpStatus.CONFLICT, "审批单不是待审批状态：%s");

    private final HttpStatus httpStatus;
    private final String message;
}
