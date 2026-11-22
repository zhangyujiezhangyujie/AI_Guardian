package com.aiguardian.domain.model;

/** 工具网关拒绝原因，按决策链顺序排列（契约 3.1、7.10，P1）。 */
public enum ToolDenyReason {
    TOOL_NOT_ALLOWED,
    PERMISSION_DENIED,
    INVALID_ARGUMENTS,
    RECORD_NOT_FOUND,
    BASIS_NOT_FOUND,
    UNVERIFIED_BASIS
}
