package com.aiguardian.demo;

import com.aiguardian.domain.model.LLMPurpose;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一个固定测试场景，字段与 test-cases/*.json 一一对应（契约 10.2、10.6）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scenario {
    private String scenarioId;
    private String name;
    private String description;
    private String priority;
    private String question;
    private boolean supportsReal;
    private List<String> recommendedStrategies;
    private Map<LLMPurpose, List<MockResponse>> mock;
    private ScenarioExpected expected;
}
