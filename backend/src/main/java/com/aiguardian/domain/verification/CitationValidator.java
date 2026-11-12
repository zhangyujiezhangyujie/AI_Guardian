package com.aiguardian.domain.verification;

import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.VerificationReport;
import java.util.List;

public interface CitationValidator {
    /** 就地校验 COMPLETED 或 PARTIAL 报告，其他状态原样返回。 */
    VerificationReport validate(VerificationReport report, List<Evidence> evidence);
}
