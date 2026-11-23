package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.ApprovalRequest;
import com.aiguardian.domain.model.ApprovalStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用内存审批仓库，语义与 JdbcApprovalRepository 一致（条件更新返回受影响行数）。 */
public class InMemoryApprovalRepository implements ApprovalRepository {

    private final Map<String, ApprovalRequest> rows = new LinkedHashMap<>();

    private final Map<String, String> auditIds = new LinkedHashMap<>();

    @Override
    public void insert(ApprovalRequest approval, String auditRequestId) {
        rows.put(approval.getApprovalId(), approval.toBuilder().build());
        auditIds.put(approval.getApprovalId(), auditRequestId);
    }

    @Override
    public Optional<ApprovalRequest> findById(String approvalId) {
        return Optional.ofNullable(rows.get(approvalId)).map(a -> a.toBuilder().build());
    }

    @Override
    public List<ApprovalRequest> findAll(ApprovalStatus status, int limit) {
        List<ApprovalRequest> list = new ArrayList<>();
        rows.values().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .sorted(Comparator.comparing(ApprovalRequest::getCreatedAt).reversed())
                .limit(limit)
                .forEach(a -> list.add(a.toBuilder().build()));
        return list;
    }

    @Override
    public int expirePending(OffsetDateTime now) {
        int count = 0;
        for (ApprovalRequest a : rows.values()) {
            if (a.getStatus() == ApprovalStatus.PENDING && OffsetDateTime.parse(a.getExpiresAt()).isBefore(now)) {
                a.setStatus(ApprovalStatus.EXPIRED);
                a.setDecidedAt(now.toString());
                count++;
            }
        }
        return count;
    }

    @Override
    public int claim(String approvalId, String approver, String comment, OffsetDateTime now) {
        return transition(approvalId, ApprovalStatus.PENDING, ApprovalStatus.APPROVED, approver, comment, now);
    }

    @Override
    public int finish(String approvalId, ApprovalStatus status, String failureReason) {
        ApprovalRequest a = rows.get(approvalId);
        if (a == null || a.getStatus() != ApprovalStatus.APPROVED) {
            return 0;
        }
        a.setStatus(status);
        a.setFailureReason(failureReason);
        return 1;
    }

    @Override
    public int reject(String approvalId, String approver, String comment, OffsetDateTime now) {
        return transition(approvalId, ApprovalStatus.PENDING, ApprovalStatus.REJECTED, approver, comment, now);
    }

    private int transition(String id, ApprovalStatus from, ApprovalStatus to, String approver, String comment,
                           OffsetDateTime now) {
        ApprovalRequest a = rows.get(id);
        if (a == null || a.getStatus() != from || OffsetDateTime.parse(a.getExpiresAt()).isBefore(now)) {
            return 0;
        }
        a.setStatus(to);
        a.setApprover(approver);
        a.setComment(comment);
        a.setDecidedAt(now.toString());
        return 1;
    }

    /** 测试辅助：直接修改过期时间（模拟已过期）。 */
    public void forceExpiresAt(String approvalId, OffsetDateTime expiresAt) {
        rows.get(approvalId).setExpiresAt(expiresAt.toString());
    }

    public String auditIdOf(String approvalId) {
        return auditIds.get(approvalId);
    }
}
