package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import java.util.List;
import java.util.HashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!stub")
public class ExactQuoteCitationValidator implements CitationValidator {
    private final AiGuardianProperties properties;

    public ExactQuoteCitationValidator(AiGuardianProperties properties) { this.properties = properties; }

    @Override
    public VerificationReport validate(VerificationReport report, List<Evidence> evidence) {
        if (report == null || (report.getVerificationState() != VerificationState.COMPLETED
                && report.getVerificationState() != VerificationState.PARTIAL)) return report;
        var byId = new HashMap<String, Evidence>();
        if (evidence != null) for (var item : evidence) {
            if (item != null) byId.putIfAbsent(item.getEvidenceId(), item);
        }
        for (var claim : report.getClaims()) {
            String original = claim.getOriginalText();
            int start = report.getAnswer() == null || original == null || original.isEmpty()
                    ? -1 : report.getAnswer().indexOf(original);
            claim.setStartIndex(start < 0 ? null : start);
            claim.setEndIndex(start < 0 ? null : start + original.length());
            if (claim.getStatus() != ClaimStatus.SUPPORTED) continue;
            var source = byId.get(claim.getEvidenceId());
            String quote = TextNormalizer.normalize(claim.getQuote());
            InsufficientReason failure = source == null ? InsufficientReason.EVIDENCE_NOT_IN_CONTEXT
                    : quote.length() < Math.max(1, properties.getVerification().getMinQuoteChars())
                    ? InsufficientReason.QUOTE_TOO_SHORT
                    : !TextNormalizer.normalize(source.getContent()).contains(quote)
                    ? InsufficientReason.QUOTE_NOT_FOUND : null;
            claim.setQuoteExists(failure == null);
            claim.setInsufficientReason(failure);
            if (failure != null) claim.setStatus(ClaimStatus.INSUFFICIENT);
        }
        report.recalculate();
        return report;
    }
}
