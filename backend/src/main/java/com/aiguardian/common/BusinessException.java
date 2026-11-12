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

    public static BusinessException invalidRequest(String reason) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, null,
                ErrorCode.INVALID_REQUEST.getMessage().formatted(reason));
    }
}
