package com.aiguardian.infrastructure.llm;

import com.aiguardian.domain.model.LLMRequest;
import com.aiguardian.domain.model.RunMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutingLLMClient implements LLMClient {
    private final ObjectProvider<LLMClient> real;
    private final ObjectProvider<LLMClient> mock;
    private final ObjectProvider<LLMClient> stub;

    public RoutingLLMClient(@Qualifier("realLLMClient") ObjectProvider<LLMClient> real,
                            @Qualifier("mockLLMClient") ObjectProvider<LLMClient> mock,
                            @Qualifier("stubLLMClient") ObjectProvider<LLMClient> stub) {
        this.real = real;
        this.mock = mock;
        this.stub = stub;
    }

    @Override
    public String generate(LLMRequest request) {
        LLMClient delegate = request.getMode() == RunMode.MOCK
                ? mock.getIfAvailable()
                : real.getIfAvailable(stub::getIfAvailable);
        if (delegate == null) {
            throw new LLMException(LLMException.Type.API_ERROR, "当前运行模式的模型实现尚未安装。");
        }
        return delegate.generate(request);
    }
}
