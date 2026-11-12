package com.aiguardian.stub;

import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.verification.CitationValidator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("stub")
public class StubCitationValidator implements CitationValidator {
    @Override
    public VerificationReport validate(VerificationReport report, List<Evidence> evidence) {
        VerificationState state = report.getVerificationState();
        if (state != VerificationState.COMPLETED && state != VerificationState.PARTIAL) {
            return report;
        }
        for (var claim : report.getClaims()) {
            if (claim.getStatus() == ClaimStatus.SUPPORTED) {
                claim.setQuoteExists(true);
            }
            int start = report.getAnswer().indexOf(claim.getOriginalText());
            claim.setStartIndex(start < 0 ? null : start);
            claim.setEndIndex(start < 0 ? null : start + claim.getOriginalText().length());
        }
        report.recalculate();
        return report;
    }
}
