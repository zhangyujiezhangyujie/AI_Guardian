package com.aiguardian.domain.model;

import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationReportTest {
    @ParameterizedTest
    @CsvSource(value = {"0,0,NULL", "2,1,0.5", "3,1,0.6667"}, nullValues = "NULL")
    void recalculatesCountsAndRatio(int total, int supported, Double ratio) {
        var claims = IntStream.range(0, total)
                .mapToObj(index -> ClaimVerification.builder()
                        .status(index < supported ? ClaimStatus.SUPPORTED : ClaimStatus.INSUFFICIENT).build())
                .toList();
        var report = VerificationReport.builder().claims(claims).build();

        report.recalculate();

        assertThat(report.getTotalClaims()).isEqualTo(total);
        assertThat(report.getSupportedClaims()).isEqualTo(supported);
        assertThat(report.getInsufficientClaims()).isEqualTo(total - supported);
        assertThat(report.getUnsupportedRatio()).isEqualTo(ratio);
    }
}
