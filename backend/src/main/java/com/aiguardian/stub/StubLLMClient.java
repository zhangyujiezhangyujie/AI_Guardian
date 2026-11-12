package com.aiguardian.stub;

import com.aiguardian.domain.model.LLMRequest;
import com.aiguardian.infrastructure.llm.LLMClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("stubLLMClient")
@Profile("stub")
public class StubLLMClient implements LLMClient {
    @Override
    public String generate(LLMRequest request) {
        return "员工每年享有15天年假，每月还有2,000元交通补贴。";
    }
}
