package com.aiguardian.infrastructure.lucene;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 按契约 6.4 把文档正文切分为片段。
 *
 * <p>实现要点：
 * <ul>
 *   <li>片段内容直接取自正文的字符区间 {@code body.substring(start, end)}，不重新拼接字符串，
 *       因此每个片段的 content 必然是原文的子串，满足契约 3.2“与索引中存储的文本逐字一致”
 *       和 EvidenceContentTest 的要求。</li>
 *   <li>正文段落之间本来就有空行，累加时保留原文的空行，不用单换行重排。</li>
 *   <li>重叠取“源文中上一片段结束位置往前 overlap 个字符”的区间，因此重叠长度严格等于配置值，
 *       且 content 仍是原文子串（不会把上一段的尾部字符串拼接过来造成非子串）。</li>
 * </ul>
 *
 * <p>切分结果与输入完全确定：同样的文档和参数每次得到相同的 evidenceId 和 content。
 */
@Component
public class Chunker {

    private final AiGuardianProperties properties;

    public Chunker(AiGuardianProperties properties) {
        this.properties = properties;
    }

    public List<Evidence> chunk(LoadedDocument document) {
        var retrieval = properties.getRetrieval();
        int min = Math.max(1, retrieval.getChunkMinChars());
        int max = Math.max(min, retrieval.getChunkMaxChars());
        int overlap = Math.max(0, retrieval.getChunkOverlapChars());
        String body = document.body();

        List<Evidence> chunks = new ArrayList<>();
        for (Section section : sections(body)) {
            int previousEnd = -1;
            for (int[] range : chunkRanges(body, section, min, max)) {
                int start = previousEnd < 0 || overlap == 0
                        ? range[0]
                        : Math.max(section.start(), previousEnd - overlap);
                chunks.add(Evidence.builder()
                        .documentId(document.documentId())
                        .documentTitle(document.title())
                        .section(section.title())
                        .content(body.substring(start, range[1]))
                        .score(0)
                        .rank(0)
                        .build());
                previousEnd = range[1];
            }
        }
        for (int index = 0; index < chunks.size(); index++) {
            chunks.get(index).setEvidenceId(String.format("%s_chunk%02d", document.documentId(), index + 1));
        }
        return chunks;
    }

    /** 按 {@code ## } 标题切章节；第一个标题之前的正文归入 section=null。 */
    private List<Section> sections(String body) {
        List<int[]> headingLines = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        int lineStart = 0;
        while (lineStart <= body.length()) {
            int lineEnd = body.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = body.length();
            }
            String line = body.substring(lineStart, lineEnd);
            if (line.startsWith("## ")) {
                headingLines.add(new int[]{lineStart, lineEnd});
                titles.add(line.substring(3).trim());
            }
            if (lineEnd == body.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }

        List<Section> sections = new ArrayList<>();
        if (headingLines.isEmpty()) {
            int[] range = trim(body, 0, body.length());
            if (range != null) {
                sections.add(new Section(null, range[0], range[1]));
            }
            return sections;
        }

        int[] preamble = trim(body, 0, headingLines.get(0)[0]);
        if (preamble != null) {
            sections.add(new Section(null, preamble[0], preamble[1]));
        }
        for (int index = 0; index < headingLines.size(); index++) {
            int start = Math.min(headingLines.get(index)[1] + 1, body.length());
            int end = index + 1 < headingLines.size() ? headingLines.get(index + 1)[0] : body.length();
            int[] range = trim(body, start, end);
            if (range != null) {
                sections.add(new Section(titles.get(index), range[0], range[1]));
            }
        }
        return sections;
    }

    /** 章节内的片段区间：先按空行分段落，再把过长段落按句末标点切开，最后累加到 max。 */
    private List<int[]> chunkRanges(String body, Section section, int min, int max) {
        List<int[]> blocks = new ArrayList<>();
        for (int[] paragraph : paragraphs(body, section)) {
            if (paragraph[1] - paragraph[0] <= max) {
                blocks.add(paragraph);
            } else {
                blocks.addAll(splitLongParagraph(body, paragraph, max));
            }
        }

        List<int[]> ranges = new ArrayList<>();
        int index = 0;
        while (index < blocks.size()) {
            int start = blocks.get(index)[0];
            int end = blocks.get(index)[1];
            index++;
            while (index < blocks.size()) {
                int nextEnd = blocks.get(index)[1];
                if (nextEnd - start > max) {
                    if (end - start >= min) {
                        break;
                    }
                    end = nextEnd;
                    index++;
                    break;
                }
                end = nextEnd;
                index++;
            }
            ranges.add(new int[]{start, end});
        }
        return ranges;
    }

    private List<int[]> paragraphs(String body, Section section) {
        List<int[]> paragraphs = new ArrayList<>();
        int position = section.start();
        int paragraphStart = -1;
        while (position < section.end()) {
            int lineEnd = body.indexOf('\n', position);
            if (lineEnd < 0 || lineEnd > section.end()) {
                lineEnd = section.end();
            }
            if (body.substring(position, lineEnd).isBlank()) {
                if (paragraphStart >= 0) {
                    addTrimmed(paragraphs, body, paragraphStart, position);
                    paragraphStart = -1;
                }
            } else if (paragraphStart < 0) {
                paragraphStart = position;
            }
            if (lineEnd == section.end()) {
                break;
            }
            position = lineEnd + 1;
        }
        if (paragraphStart >= 0) {
            addTrimmed(paragraphs, body, paragraphStart, section.end());
        }
        return paragraphs;
    }

    /** 超长段落在句末标点处切开，每块尽量接近 max；找不到标点时按 max 硬切。 */
    private List<int[]> splitLongParagraph(String body, int[] paragraph, int max) {
        List<int[]> pieces = new ArrayList<>();
        int position = paragraph[0];
        while (paragraph[1] - position > max) {
            int cut = -1;
            for (int candidate = position + max; candidate > position; candidate--) {
                char c = body.charAt(candidate - 1);
                if (c == '。' || c == '！' || c == '？' || c == '；') {
                    cut = candidate;
                    break;
                }
            }
            if (cut <= position) {
                cut = position + max;
            }
            addTrimmed(pieces, body, position, cut);
            position = cut;
            int[] rest = trim(body, position, paragraph[1]);
            position = rest == null ? paragraph[1] : rest[0];
        }
        if (position < paragraph[1]) {
            addTrimmed(pieces, body, position, paragraph[1]);
        }
        return pieces;
    }

    private void addTrimmed(List<int[]> target, String body, int start, int end) {
        int[] range = trim(body, start, end);
        if (range != null) {
            target.add(range);
        }
    }

    private int[] trim(String body, int start, int end) {
        int from = Math.max(0, start);
        int to = Math.min(body.length(), end);
        while (from < to && Character.isWhitespace(body.charAt(from))) {
            from++;
        }
        while (to > from && Character.isWhitespace(body.charAt(to - 1))) {
            to--;
        }
        return from >= to ? null : new int[]{from, to};
    }

    private record Section(String title, int start, int end) {
    }
}
