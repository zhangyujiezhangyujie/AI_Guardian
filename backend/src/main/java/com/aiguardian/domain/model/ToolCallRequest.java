package com.aiguardian.domain.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工具调用请求（契约 3.11，P1）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String basedOnRequestId;
}
