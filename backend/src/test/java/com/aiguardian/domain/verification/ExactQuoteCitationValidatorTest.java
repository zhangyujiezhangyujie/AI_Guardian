package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactQuoteCitationValidatorTest {
    final ExactQuoteCitationValidator validator = new ExactQuoteCitationValidator(new AiGuardianProperties());

    ClaimVerification claim(String id, String quote) {
        return ClaimVerification.builder().claimId("c1").originalText("年假15天")
                .status(ClaimStatus.SUPPORTED).modelSupported(true).evidenceId(id).quote(quote).build();
    }

    @Test void checksInOrderAndUpdatesCountsAndUtf16Offsets() {
        var ok = claim("e1", "正式员工年假１５天");
        var wrongId = claim("missing", "短");
        var shortQuote = claim("e1", "年假");
        var invented = claim("e1", "正式员工年假30天");
        var unsupported = claim("missing", "任何内容");
        unsupported.setStatus(ClaimStatus.INSUFFICIENT);
        unsupported.setInsufficientReason(InsufficientReason.MODEL_UNSUPPORTED);
        var report = VerificationReport.builder().answer("😀年假15天")
                .verificationState(VerificationState.COMPLETED)
                .claims(List.of(ok, wrongId, shortQuote, invented, unsupported)).build();
        assertSame(report, validator.validate(report, List.of(Evidence.builder().evidenceId("e1")
                .content("正式员工年假15天。").build())));
        assertEquals(1, report.getSupportedClaims());
        assertEquals(0.8, report.getUnsupportedRatio());
        assertTrue(ok.getQuoteExists());
        assertNull(ok.getInsufficientReason());
        assertEquals(2, ok.getStartIndex());
        assertEquals(7, ok.getEndIndex());
        assertEquals(InsufficientReason.EVIDENCE_NOT_IN_CONTEXT, wrongId.getInsufficientReason());
        assertEquals(InsufficientReason.QUOTE_TOO_SHORT, shortQuote.getInsufficientReason());
        assertEquals(InsufficientReason.QUOTE_NOT_FOUND, invented.getInsufficientReason());
        assertFalse(invented.getQuoteExists());
        assertNull(unsupported.getQuoteExists());
        assertEquals(2, unsupported.getStartIndex());
    }

    @Test void doesNotNormalizeAnswerOffsetsAndHandlesPartial() {
        var c = claim("e1", "正式员工年假15天");
        var report = VerificationReport.builder().answer("年假１５天")
                .verificationState(VerificationState.PARTIAL).claims(List.of(c)).build();
        validator.validate(report, List.of(Evidence.builder().evidenceId("e1").content(c.getQuote()).build()));
        assertTrue(c.getQuoteExists());
        assertNull(c.getStartIndex());
        assertNull(c.getEndIndex());
    }

    @Test void otherStatesAreUntouched() {
        for (var state : List.of(VerificationState.FAILED, VerificationState.NO_CLAIMS, VerificationState.NO_EVIDENCE)) {
            var c = claim("missing", "missing");
            var report = VerificationReport.builder().verificationState(state).claims(List.of(c)).build();
            assertSame(report, validator.validate(report, List.of()));
            assertNull(c.getQuoteExists());
            assertEquals(ClaimStatus.SUPPORTED, c.getStatus());
        }
    }

    @Test void normalizesContractPunctuationWhitespaceAndThousands() {
        assertEquals("\"abc\",'x':2000;ok!?.,", TextNormalizer.normalize("「ＡＢＣ」，‘X’：２,０００；OK！？。、\u3000\n"));
        assertEquals("", TextNormalizer.normalize(null));
    }
}
