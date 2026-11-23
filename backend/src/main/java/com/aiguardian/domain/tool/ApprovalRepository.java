package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 审批单仓库（契约 9：approval_request，P1 v1.4）。
 * 状态变更全部是条件更新，返回受影响行数，用于保证并发时只处理一次。
 */
public interface ApprovalRepository {

    void insert(ApprovalRequest approval, String auditRequestId);

    Optional<ApprovalRequest> findById(String approvalId);

    /** 按创建时间降序；status 为 null 时不过滤。 */
    List<ApprovalRequest> findAll(ApprovalStatus status, int limit);

    /** 把已超过有效期的 PENDING 置为 EXPIRED。 */
    int expirePending(OffsetDateTime now);

    /** PENDING → APPROVED（抢占）。 */
    int claim(String approvalId, String approver, String comment, OffsetDateTime now);

    /** APPROVED → EXECUTED 或 FAILED。 */
    int finish(String approvalId, ApprovalStatus status, String failureReason);

    /** PENDING → REJECTED。 */
    int reject(String approvalId, String approver, String comment, OffsetDateTime now);
}
