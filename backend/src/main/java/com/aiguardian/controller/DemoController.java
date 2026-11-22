package com.aiguardian.controller;

import com.aiguardian.application.ChatCommand;
import com.aiguardian.application.ChatOrchestrator;
import com.aiguardian.application.ToolCallService;
import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.demo.Scenario;
import com.aiguardian.demo.ScenarioCheckResult;
import com.aiguardian.demo.ScenarioExpectationChecker;
import com.aiguardian.demo.ScenarioRepository;
import com.aiguardian.demo.ScenarioToolCall;
import com.aiguardian.demo.ToolExpectationChecker;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.ChatResponse;
import com.aiguardian.domain.model.RunMode;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示接口（契约 7.6、7.7）：
 * 列出固定场景，或按场景运行一次问答并被期望比对器检查。
 *
 * <p>MOCK 模式只替换模型输出，检索、核验、引用校验、输出防护都走真实实现；
 * REAL 模式不比对，返回 checked=false。
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Profile("!stub")
public class DemoController {

    private static final List<String> VERIFIERS = List.of("batch", "per-claim", "none");

    private final ScenarioRepository scenarios;
    private final ChatOrchestrator orchestrator;
    private final ScenarioExpectationChecker checker;
    private final OutputGuardRegistry guards;
    private final ToolCallService tools;

    /** 7.6 场景列表，按 scenarioId 升序，不返回 mock 与 expected。 */
    @GetMapping("/scenarios")
    public List<ScenarioSummary> scenarios() {
        return scenarios.findAll().stream()
                .map(scenario -> new ScenarioSummary(scenario.getScenarioId(), scenario.getName(),
                        scenario.getDescription(), scenario.getQuestion(), scenario.getPriority(),
                        scenario.isSupportsReal(), scenario.getRecommendedStrategies()))
                .toList();
    }

    /** 7.7 运行场景。参数校验失败返回 400，场景不存在返回 404，其余情况始终返回 200 和 {response, check}。 */
    @PostMapping("/run")
    public DemoRunResponse run(@RequestBody RunRequest request) {
        validate(request);
        Scenario scenario = scenarios.findById(request.scenarioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENARIO_NOT_FOUND));
        if (request.mode() == RunMode.REAL && !scenario.isSupportsReal()) {
            throw BusinessException.invalidRequest("该场景不支持真实模型模式。");
        }
        if (request.verifier() != null && request.mode() == RunMode.MOCK) {
            throw BusinessException.invalidRequest("只有 REAL 模式允许覆盖核验器。");
        }
        if (request.guardStrategy() != null && !guards.availableStrategies().contains(request.guardStrategy())) {
            throw BusinessException.invalidRequest("输出防护策略不存在。");
        }

        ChatCommand command = ChatCommand.builder()
                .question(scenario.getQuestion())
                .mode(request.mode())
                .scenarioId(scenario.getScenarioId())
                .guardStrategyOverride(request.guardStrategy())
                .blockThresholdOverride(request.blockThreshold())
                .verifierOverride(request.verifier())
                .build();

        ChatResponse response;
        int httpStatus = 200;
        String errorCode = null;
        try {
            response = orchestrator.handle(command);
        } catch (BusinessException e) {
            response = null;
            httpStatus = e.getErrorCode().getHttpStatus().value();
            errorCode = e.getErrorCode().name();
        }

        ScenarioCheckResult check = request.mode() == RunMode.MOCK
                ? checker.check(scenario, response, httpStatus, errorCode)
                : new ScenarioCheckResult(false, null, List.of());

        // v1.3（契约 7.7、10.7）：场景中模型提出的工具调用交给工具网关，依据为本次核验报告
        ToolCallResult toolCall = null;
        if (scenario.getToolCall() != null && request.mode() == RunMode.MOCK && response != null) {
            ScenarioToolCall proposed = scenario.getToolCall();
            toolCall = tools.executeWithBasis(ToolCallRequest.builder()
                            .toolName(proposed.getToolName())
                            .arguments(proposed.getArguments())
                            .basedOnRequestId(response.getRequestId())
                            .build(),
                    proposed.getUser(),
                    response.getVerification());
        }
        if (request.mode() == RunMode.MOCK && scenario.getExpected().getToolCall() != null) {
            List<String> toolMismatches = ToolExpectationChecker.mismatches(
                    scenario.getExpected().getToolCall(), toolCall);
            if (!toolMismatches.isEmpty()) {
                List<String> merged = new ArrayList<>(check.mismatches());
                merged.addAll(toolMismatches);
                check = new ScenarioCheckResult(true, false, merged);
            }
        }
        return new DemoRunResponse(response, check, toolCall);
    }

    private static void validate(RunRequest request) {
        if (request == null || request.scenarioId() == null || request.scenarioId().isBlank()) {
            throw BusinessException.invalidRequest("scenarioId 必填。");
        }
        if (request.mode() == null) {
            throw BusinessException.invalidRequest("mode 必填，取值为 MOCK 或 REAL。");
        }
        if (request.verifier() != null && !VERIFIERS.contains(request.verifier())) {
            throw BusinessException.invalidRequest("verifier 取值为 batch、per-claim 或 none。");
        }
        Double threshold = request.blockThreshold();
        if (threshold != null && (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)) {
            throw BusinessException.invalidRequest("blockThreshold 必须在 0 到 1 之间。");
        }
    }

    public record ScenarioSummary(String scenarioId, String name, String description, String question,
                                  String priority, boolean supportsReal, List<String> recommendedStrategies) {
    }

    public record RunRequest(String scenarioId, RunMode mode, String guardStrategy, Double blockThreshold,
                             String verifier) {
    }

    public record DemoRunResponse(ChatResponse response, ScenarioCheckResult check, ToolCallResult toolCall) {
    }
}
