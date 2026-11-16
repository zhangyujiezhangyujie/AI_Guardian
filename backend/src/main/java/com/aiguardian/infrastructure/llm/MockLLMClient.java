package com.aiguardian.infrastructure.llm;

import com.aiguardian.demo.MockResponse;
import com.aiguardian.demo.Scenario;
import com.aiguardian.demo.ScenarioRepository;
import com.aiguardian.domain.model.LLMRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.aiguardian.infrastructure.llm.LLMException.Type.API_ERROR;
import static com.aiguardian.infrastructure.llm.LLMException.Type.EMPTY_RESPONSE;
import static com.aiguardian.infrastructure.llm.LLMException.Type.TIMEOUT;

/**
 * 模拟模型（契约 4.1、10.3）：按场景文件返回固定内容，不访问网络，也不参与任何核验或判断。
 *
 * <p>bean 名必须是 {@code mockLLMClient}，{@link RoutingLLMClient} 按这个名字注入。
 */
@Slf4j
@Component("mockLLMClient")
@Profile("!stub")
public class MockLLMClient implements LLMClient {

    private final ScenarioRepository repository;

    public MockLLMClient(ScenarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public String generate(LLMRequest request) {
        if (request == null) {
            throw new LLMException(API_ERROR, "模拟调用缺少请求。");
        }
        String scenarioId = request.getScenarioId();
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new LLMException(API_ERROR, "MOCK 模式必须提供 scenarioId。");
        }
        Scenario scenario = repository.findById(scenarioId)
                .orElseThrow(() -> new LLMException(API_ERROR, "场景不存在：" + scenarioId));
        List<MockResponse> responses = scenario.getMock() == null ? null : scenario.getMock().get(request.getPurpose());
        if (responses == null || responses.isEmpty()) {
            throw new LLMException(API_ERROR, "场景未配置该调用：" + request.getPurpose());
        }
        int attempt = Math.max(1, request.getAttempt());
        MockResponse response = responses.get(Math.min(attempt, responses.size()) - 1);
        sleepIfNeeded(response.getDelayMs());
        String type = response.getType() == null ? "" : response.getType();
        return switch (type) {
            case "TEXT" -> text(response);
            case "TIMEOUT" -> throw new LLMException(TIMEOUT, "模拟超时");
            case "API_ERROR" -> throw new LLMException(API_ERROR, "模拟接口错误");
            case "EMPTY" -> throw new LLMException(EMPTY_RESPONSE, "模拟空响应");
            default -> throw new LLMException(API_ERROR, "未知的模拟响应类型：" + type);
        };
    }

    private static String text(MockResponse response) {
        if (response.getContent() == null) {
            throw new LLMException(API_ERROR, "TEXT 响应缺少 content。");
        }
        return response.getContent();
    }

    private static void sleepIfNeeded(Long delayMs) {
        if (delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException(API_ERROR, "模拟延迟被中断。");
        }
    }
}
