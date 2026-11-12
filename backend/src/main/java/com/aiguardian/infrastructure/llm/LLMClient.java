package com.aiguardian.infrastructure.llm;

import com.aiguardian.domain.model.LLMRequest;

public interface LLMClient {
    /** 返回非空模型文本，失败时抛出 LLMException。 */
    String generate(LLMRequest request);
}
