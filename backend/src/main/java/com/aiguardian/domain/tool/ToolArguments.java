package com.aiguardian.domain.tool;

/**
 * 通过 ToolCatalog 校验后的工具参数。未用到的字段为 null。
 */
public record ToolArguments(String keyword, Long recordId, String status) {
}
