package com.aiguardian.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String requestId;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String requestId) {
        this(errorCode, requestId, errorCode == ErrorCode.INVALID_REQUEST
                ? errorCode.getMessage().formatted("请检查请求字段。")
                : errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String requestId, String message) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    /** 带原因的错误（message 模板含 %s 的错误码，如 APPROVAL_FORBIDDEN）。 */
    public static BusinessException withReason(ErrorCode errorCode, String reason) {
        return new BusinessException(errorCode, null, errorCode.getMessage().formatted(reason));
    }

    public static BusinessException invalidRequest(String reason) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, null,
                ErrorCode.INVALID_REQUEST.getMessage().formatted(reason));
    }
}
