package com.aiguardian.domain.model;

/** 审批单状态（契约 3.1，P1 v1.4）。 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    EXECUTED,
    FAILED,
    REJECTED,
    EXPIRED
}
