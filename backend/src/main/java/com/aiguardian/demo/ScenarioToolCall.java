package com.aiguardian.demo;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场景中“模型提出的工具调用”（契约 10.6、10.7，v1.3）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioToolCall {
    private String user;
    private String toolName;
    private Map<String, Object> arguments;
}
