package com.aiguardian.domain.guard;

import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;
import org.springframework.stereotype.Component;

import static com.aiguardian.domain.guard.AnnotateOutputGuard.BLOCKED;
import static com.aiguardian.domain.guard.AnnotateOutputGuard.result;

@Component
public class StrictOutputGuard implements OutputGuard {
    private final AnnotateOutputGuard annotate;

    public StrictOutputGuard(AnnotateOutputGuard annotate) {
        this.annotate = annotate;
    }

    @Override
    public String name() {
        return "strict";
    }

    @Override
    public GuardResult evaluate(VerificationReport report, GuardPolicy policy) {
        if (report.getInsufficientClaims() > 0) {
            return result(report, policy, GuardAction.BLOCKED, false, null, BLOCKED);
        }
        return annotate.evaluate(report, policy);
    }
}
