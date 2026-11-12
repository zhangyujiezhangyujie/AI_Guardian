package com.aiguardian.controller;

import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.common.ShanghaiTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return response(e.getErrorCode().getHttpStatus(), e.getErrorCode(), e.getRequestId(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable() {
        return business(BusinessException.invalidRequest("请求体缺失或 JSON 格式错误。"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return response(error.getStatusCode(), ErrorCode.INVALID_REQUEST, null,
                    ErrorCode.INVALID_REQUEST.getMessage().formatted("请检查请求路径、方法和参数。"));
        }
        log.error("exceptionType={}", e.getClass().getName());
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return response(code.getHttpStatus(), code, null, code.getMessage());
    }

    private ResponseEntity<ApiError> response(HttpStatusCode status, ErrorCode code, String requestId, String message) {
        return ResponseEntity.status(status).body(new ApiError(requestId, code, message, ShanghaiTime.now().toString()));
    }

    public record ApiError(String requestId, ErrorCode errorCode, String message, String timestamp) {}
}
