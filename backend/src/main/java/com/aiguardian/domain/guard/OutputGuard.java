package com.aiguardian.domain.guard;

import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;

public interface OutputGuard {
    String name();

    /** 评估展示方式，保持原报告完整。 */
    GuardResult evaluate(VerificationReport report, GuardPolicy policy);
}
