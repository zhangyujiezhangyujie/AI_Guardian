package com.aiguardian.domain.evidence;

import com.aiguardian.domain.model.Evidence;
import java.util.List;
import java.util.stream.Collectors;

public final class EvidenceFormatter {
    private EvidenceFormatter() {}

    public static String format(List<Evidence> evidence) {
        return evidence.stream().map(EvidenceFormatter::formatChunk)
                .collect(Collectors.joining("\n\n"));
    }

    private static String formatChunk(Evidence chunk) {
        String section = chunk.getSection() == null ? "" : "· " + chunk.getSection();
        return "[%s]《%s》%s\n%s".formatted(
                chunk.getEvidenceId(), chunk.getDocumentTitle(), section, chunk.getContent());
    }
}
