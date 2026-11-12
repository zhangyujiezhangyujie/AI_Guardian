package com.aiguardian.infrastructure.llm;

import lombok.Getter;

@Getter
public class LLMException extends RuntimeException {
    public enum Type { TIMEOUT, API_ERROR, EMPTY_RESPONSE }

    private final Type type;

    public LLMException(Type type) {
        this(type, type.name());
    }

    public LLMException(Type type, String message) {
        super(message);
        this.type = type;
    }
}
