package com.aiguardian.domain.guard;

import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;
import org.springframework.stereotype.Component;

import static com.aiguardian.domain.guard.AnnotateOutputGuard.BLOCKED;
import static com.aiguardian.domain.guard.AnnotateOutputGuard.FAILED_HIDDEN;
import static com.aiguardian.domain.guard.AnnotateOutputGuard.result;

@Component
public class BlockOutputGuard implements OutputGuard {
    private final AnnotateOutputGuard annotate;

    public BlockOutputGuard(AnnotateOutputGuard annotate) {
        this.annotate = annotate;
    }

    @Override
    public String name() {
        return "block";
    }

    @Override
    public GuardResult evaluate(VerificationReport report, GuardPolicy policy) {
        return switch (report.getVerificationState()) {
            case FAILED -> result(report, policy, GuardAction.NOT_VERIFIED, false, null, FAILED_HIDDEN);
            case COMPLETED, PARTIAL -> {
                Double ratio = report.getUnsupportedRatio();
                yield ratio != null && ratio > policy.getBlockThreshold()
                        ? result(report, policy, GuardAction.BLOCKED, false, null, BLOCKED)
                        : annotate.evaluate(report, policy);
            }
            case NO_EVIDENCE, NO_CLAIMS -> annotate.evaluate(report, policy);
        };
    }
}
