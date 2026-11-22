package com.aiguardian.domain.verification;

import com.aiguardian.domain.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Evaluation only. Never sends labels to a model or changes production verification. */
final class Phase4Metrics {
    private Phase4Metrics() {}
    record Span(int start, int end, String text) {}
    record Alignment(String expectedId, String expectedStatus, String outcome,
                     Integer outputIndex, String predictedStatus, Integer start, Integer end) {}
    record CaseScore(List<Alignment> expected, List<Integer> extraOutputs,
                     List<Integer> unalignableOutputs, List<Integer> duplicateOutputs) {}

    // Only sentence-final punctuation/outer whitespace may differ; internal text must be exact.
    static Span span(String answer, String text) {
        if (text == null || text.isBlank()) return null;
        String core = text.strip().replaceFirst("[。.!！?？;；]+$", "").strip();
        if (core.isEmpty()) return null;
        int start = answer.indexOf(core);
        if (start < 0 || answer.indexOf(core, start + 1) >= 0) return null;
        return new Span(start, start + core.length(), core);
    }

    static CaseScore align(JsonNode fixture, VerificationReport report) {
        String answer = fixture.get("answer").asText();
        var output = report.getClaims();
        var expected = new ArrayList<Alignment>();
        var extra = new ArrayList<Integer>();
        var invalid = new ArrayList<Integer>();
        var duplicates = new ArrayList<Integer>();
        var used = new HashSet<Integer>();
        var spans = new ArrayList<Span>();
        for (int i = 0; i < output.size(); i++) {
            var claim = output.get(i);
            Span span = span(answer, claim.getOriginalText());
            // Position must also be the validator's exact UTF-16 originalText interval.
            Integer start = claim.getStartIndex(), end = claim.getEndIndex();
            if (start == null || end == null || start < 0 || end < start || end > answer.length()
                    || !answer.substring(start, end).equals(claim.getOriginalText())) span = null;
            spans.add(span);
            if (span == null) invalid.add(i);
        }
        for (var label : fixture.get("claims")) {
            Span target = span(answer, label.get("originalText").asText());
            var matches = new ArrayList<Integer>();
            if (target != null) for (int i = 0; i < spans.size(); i++) {
                if (target.equals(spans.get(i))) matches.add(i);
            }
            String outcome = report.getVerificationState() == VerificationState.FAILED ? "CALL_FAILED"
                    : target == null ? "EXPECTED_AMBIGUOUS"
                    : matches.isEmpty() ? "MISSING_EXTRACTION"
                    : matches.size() > 1 ? "DUPLICATE_AMBIGUOUS" : "MATCHED";
            used.addAll(matches);
            if (matches.size() > 1) duplicates.addAll(matches);
            Integer index = outcome.equals("MATCHED") ? matches.get(0) : null;
            expected.add(new Alignment(label.get("id").asText(), label.get("proposedStatus").asText(),
                    outcome, index, index == null ? null : output.get(index).getStatus().name(),
                    target == null ? null : target.start(), target == null ? null : target.end()));
        }
        for (int i = 0; i < output.size(); i++) if (!used.contains(i)) extra.add(i);
        return new CaseScore(expected, extra, invalid, duplicates);
    }

    static Map<String, Object> summarize(List<CaseScore> scores, List<VerificationReport> reports) {
        int tp=0, tn=0, fp=0, fn=0, unresolvedPositive=0, unresolvedNegative=0;
        var outcomes = new TreeMap<String, Integer>();
        for (var score : scores) for (var row : score.expected()) {
            outcomes.merge(row.outcome(), 1, Integer::sum);
            boolean positive = row.expectedStatus().equals("INSUFFICIENT");
            if (!row.outcome().equals("MATCHED")) {
                if (positive) unresolvedPositive++; else unresolvedNegative++;
                continue;
            }
            boolean predicted = row.predictedStatus().equals("INSUFFICIENT");
            if (positive && predicted) tp++; else if (positive) fn++;
            else if (predicted) fp++; else tn++;
        }
        int aligned=tp+tn+fp+fn, total=aligned+unresolvedPositive+unresolvedNegative;
        int passed=0, failed=0, notChecked=0;
        var citationReasons = new TreeMap<String, Integer>();
        for (var report : reports) for (var claim : report.getClaims()) {
            if (Boolean.TRUE.equals(claim.getQuoteExists())) passed++;
            else if (Boolean.FALSE.equals(claim.getQuoteExists())) {
                failed++;
                citationReasons.merge(String.valueOf(claim.getInsufficientReason()), 1, Integer::sum);
            } else notChecked++;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("positive", "INSUFFICIENT");
        result.put("confusionMatrix", Map.of("TP",tp,"TN",tn,"FP",fp,"FN",fn));
        result.put("totalExpected", total);
        result.put("aligned", aligned);
        result.put("unresolvedPositive", unresolvedPositive);
        result.put("unresolvedNegative", unresolvedNegative);
        result.put("outcomes", outcomes);
        result.put("alignedAccuracy", ratio(tp+tn,aligned));
        result.put("falsePositiveRate", ratio(fp,fp+tn));
        result.put("falseNegativeRate", ratio(fn,fn+tp));
        result.put("alignmentCoverage", ratio(aligned,total));
        result.put("strictCorrectOverAllExpected", ratio(tp+tn,total));
        result.put("extraOutputs", scores.stream().mapToInt(s->s.extraOutputs().size()).sum());
        result.put("unalignableOutputs", scores.stream().mapToInt(s->s.unalignableOutputs().size()).sum());
        result.put("duplicateOutputs", scores.stream().mapToInt(s->s.duplicateOutputs().size()).sum());
        result.put("failedCases", reports.stream().filter(r->r.getVerificationState()==VerificationState.FAILED).count());
        result.put("partialCases", reports.stream().filter(r->r.getVerificationState()==VerificationState.PARTIAL).count());
        result.put("citationPassed", passed);
        result.put("citationFailed", failed);
        result.put("citationNotChecked", notChecked);
        result.put("citationFailureReasons", citationReasons);
        return result;
    }
    private static Double ratio(int numerator, int denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
