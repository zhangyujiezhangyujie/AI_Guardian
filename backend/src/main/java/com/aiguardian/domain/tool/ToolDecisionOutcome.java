package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.ToolDenyReason;

/**
 * 决策链的结果。arguments 仅在参数校验通过后非空（ALLOW 时一定非空）。
 */
public record ToolDecisionOutcome(ToolDecision decision, ToolDenyReason denyReason, String message,
                                  ToolArguments arguments) {
}
