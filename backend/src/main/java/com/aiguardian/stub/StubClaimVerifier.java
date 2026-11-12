package com.aiguardian.stub;

import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.InsufficientReason;
import com.aiguardian.domain.model.VerificationInput;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.verification.ClaimVerifier;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("stub")
public class StubClaimVerifier implements ClaimVerifier {
    @Override
    public String name() {
        return "batch";
    }

    @Override
    public VerificationReport verify(VerificationInput input) {
        ClaimVerification leave = ClaimVerification.builder()
                .claimId("c1").claim("员工每年享有15天年假").originalText("员工每年享有15天年假")
                .modelSupported(true).status(ClaimStatus.SUPPORTED)
                .evidenceId("doc001_chunk03").quote("正式员工每年享有15天带薪年假").build();
        ClaimVerification subsidy = ClaimVerification.builder()
                .claimId("c2").claim("员工每月有2000元交通补贴").originalText("每月还有2,000元交通补贴")
                .status(ClaimStatus.INSUFFICIENT).insufficientReason(InsufficientReason.MODEL_UNSUPPORTED).build();
        VerificationReport report = VerificationReport.builder()
                .requestId(input.getRequestId()).answer(input.getAnswer()).verificationState(VerificationState.COMPLETED)
                .claims(List.of(leave, subsidy)).verifierName(name()).llmCalls(0).build();
        report.recalculate();
        return report;
    }
}
