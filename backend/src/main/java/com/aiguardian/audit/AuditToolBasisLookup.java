package com.aiguardian.audit;

import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.tool.ToolBasisLookup;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 通过审计查询读取依据报告（契约 7.10）。生成失败的记录没有核验报告，视为不存在。 */
@Component
@Profile("!stub")
public class AuditToolBasisLookup implements ToolBasisLookup {

    private final AuditQueryService audit;

    public AuditToolBasisLookup(AuditQueryService audit) {
        this.audit = audit;
    }

    @Override
    public Optional<VerificationReport> find(String requestId) {
        try {
            return Optional.ofNullable(audit.findByRequestId(requestId).verification());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.AUDIT_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
