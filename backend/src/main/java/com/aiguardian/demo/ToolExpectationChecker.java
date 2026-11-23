package com.aiguardian.demo;

import com.aiguardian.domain.model.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 比对工具网关决策与场景期望（契约 7.7、10.7，v1.3）。
 * 不改动成员 4 的 ScenarioExpectationChecker 接口，结果由 DemoController 并入 check.mismatches。
 */
public final class ToolExpectationChecker {

    private ToolExpectationChecker() {
    }

    public static List<String> mismatches(ExpectedToolCall expected, ToolCallResult actual) {
        List<String> mismatches = new ArrayList<>();
        if (expected == null) {
            return mismatches;
        }
        if (actual == null) {
            mismatches.add("toolCall 期望 " + expected.getDecision() + "，实际未执行工具调用");
            return mismatches;
        }
        String decision = actual.getDecision() == null ? null : actual.getDecision().name();
        String denyReason = actual.getDenyReason() == null ? null : actual.getDenyReason().name();
        if (!Objects.equals(expected.getDecision(), decision)) {
            mismatches.add("toolCall.decision 期望 " + expected.getDecision() + "，实际 " + decision);
        }
        if (!Objects.equals(expected.getDenyReason(), denyReason)) {
            mismatches.add("toolCall.denyReason 期望 " + expected.getDenyReason() + "，实际 " + denyReason);
        }
        return mismatches;
    }
}
