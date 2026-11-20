package com.aiguardian.domain.verification;

import com.aiguardian.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase4MetricsTest {
    final ObjectMapper json=new ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode fixture() throws Exception {
        return json.readTree("""
                {"answer":"😀支持甲。缺乏乙。", "claims":[
                  {"id":"a","originalText":"支持甲。","proposedStatus":"SUPPORTED"},
                  {"id":"b","originalText":"缺乏乙。","proposedStatus":"INSUFFICIENT"}]}
                """);
    }
    ClaimVerification claim(String text,ClaimStatus status,int start) {
        return ClaimVerification.builder().originalText(text).status(status)
                .startIndex(start).endIndex(start+text.length()).build();
    }
    VerificationReport report(ClaimVerification... claims) {
        return VerificationReport.builder().verificationState(VerificationState.COMPLETED)
                .claims(List.of(claims)).build();
    }
    @Test void alignsReorderedOutputsUsingUtf16SpansAndAllowsOnlyTerminalPunctuation() throws Exception {
        var r=report(claim("缺乏乙",ClaimStatus.INSUFFICIENT,6),claim("支持甲",ClaimStatus.SUPPORTED,2));
        var s=Phase4Metrics.align(fixture(),r);
        assertEquals(1,s.expected().get(0).outputIndex());
        assertEquals(0,s.expected().get(1).outputIndex());
        var m=Phase4Metrics.summarize(List.of(s),List.of(r));
        assertEquals(1.0,m.get("alignedAccuracy"));
        assertEquals(Map.of("TP",1,"TN",1,"FP",0,"FN",0),m.get("confusionMatrix"));
    }
    @Test void missingInvalidAndDuplicateOutputsAreNotCorrectPredictions() throws Exception {
        var r=report(claim("支持甲",ClaimStatus.SUPPORTED,2),claim("支持甲",ClaimStatus.SUPPORTED,2),
                claim("改写乙",ClaimStatus.INSUFFICIENT,6));
        var s=Phase4Metrics.align(fixture(),r);
        assertEquals("DUPLICATE_AMBIGUOUS",s.expected().get(0).outcome());
        assertEquals("MISSING_EXTRACTION",s.expected().get(1).outcome());
        assertEquals(List.of(2),s.extraOutputs()); assertEquals(List.of(2),s.unalignableOutputs());
        var m=Phase4Metrics.summarize(List.of(s),List.of(r));
        assertNull(m.get("alignedAccuracy")); assertEquals(0.0,m.get("strictCorrectOverAllExpected"));
        assertEquals(1,m.get("unresolvedPositive")); assertEquals(1,m.get("unresolvedNegative"));
    }
    @Test void countsFalsePositiveFalseNegativeAndFailureAbstentionsSeparately() throws Exception {
        var r=report(claim("缺乏乙",ClaimStatus.SUPPORTED,6),claim("支持甲",ClaimStatus.INSUFFICIENT,2));
        var m=Phase4Metrics.summarize(List.of(Phase4Metrics.align(fixture(),r)),List.of(r));
        assertEquals(1.0,m.get("falsePositiveRate")); assertEquals(1.0,m.get("falseNegativeRate"));
        var failed=VerificationReport.builder().verificationState(VerificationState.FAILED).build();
        var s=Phase4Metrics.align(fixture(),failed);
        assertTrue(s.expected().stream().allMatch(a->a.outcome().equals("CALL_FAILED")));
        assertNull(Phase4Metrics.summarize(List.of(s),List.of(failed)).get("falseNegativeRate"));
        assertNull(Phase4Metrics.span("重复。重复。","重复"));
    }
    @Test void performanceSummaryExcludesWarmupAndRetainsFailuresInLatencyAndCallCounts() {
        var rows=new ArrayList<Map<String,Object>>();
        rows.add(Map.of("phase","warmup","arm","none","elapsedMs",999.0,"llmCalls",1,"success",true,"timeout",false));
        rows.add(Map.of("phase","formal","arm","none","elapsedMs",10.0,"llmCalls",1,"success",false,"timeout",true));
        rows.add(Map.of("phase","formal","arm","none","elapsedMs",30.0,"llmCalls",2,"success",true,"timeout",false));
        var result=Phase4PerformanceTest.summary(rows);
        var arm=(Map<?,?>)result.get("none");
        assertEquals(2,arm.get("attempts"));assertEquals(1,arm.get("failures"));
        assertEquals(20.0,arm.get("meanMsAllAttempts"));assertEquals(30.0,arm.get("p95MsAllAttempts"));
        assertEquals(10.0,arm.get("p50MsAllAttempts"));assertEquals(3,arm.get("llmCallsTotal"));
        assertNull(((Map<?,?>)result.get("batch")).get("p50MsAllAttempts"));
    }
}
