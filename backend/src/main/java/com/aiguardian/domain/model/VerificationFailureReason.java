package com.aiguardian.domain.model;

public enum VerificationFailureReason {
    LLM_TIMEOUT,
    LLM_API_ERROR,
    INVALID_JSON,
    SCHEMA_INVALID
}
