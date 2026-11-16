package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private final AiGuardianProperties properties = new AiGuardianProperties();
    private final Chunker chunker = new Chunker(properties);

    @Test
    void keepsChunksInsideTheirSection() {
        String body = "## 年假制度\n\n" + "正式员工每年享有15天带薪年假。".repeat(6)
                + "\n\n## 报销制度\n\n" + "报销需提交发票原件。".repeat(6) + "\n";
        LoadedDocument document = new LoadedDocument("doc001", "员工手册", "doc001_员工手册.md", body);

        List<Evidence> chunks = chunker.chunk(document);
        assertFalse(chunks.isEmpty());
        for (Evidence chunk : chunks) {
            assertTrue(chunk.getSection().equals("年假制度") || chunk.getSection().equals("报销制度"),
                    "片段的章节不属于文档：" + chunk.getSection());
            boolean containsLeave = chunk.getContent().contains("年假");
            boolean containsReimburse = chunk.getContent().contains("发票");
            assertFalse(containsLeave && containsReimburse, "片段跨越了两个章节：" + chunk.getEvidenceId());
        }
    }

    @Test
    void isDeterministicForSameInput() {
        String body = "## 考勤制度\n\n" + "工作时间为每天9:00至18:00。".repeat(20) + "\n";
        LoadedDocument document = new LoadedDocument("doc004", "考勤制度", "doc004_考勤制度.md", body);

        List<Evidence> first = chunker.chunk(document);
        List<Evidence> second = chunker.chunk(document);

        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).getEvidenceId(), second.get(index).getEvidenceId());
            assertEquals(first.get(index).getContent(), second.get(index).getContent());
        }
    }

    @Test
    void splitsOverlongParagraphAtSentenceEnd() {
        properties.getRetrieval().setChunkMinChars(200);
        properties.getRetrieval().setChunkMaxChars(400);
        properties.getRetrieval().setChunkOverlapChars(40);
        String sentence = "正式员工每年享有15天带薪年假，试用期员工不享有年假。";
        String body = "## 年假制度\n\n" + sentence.repeat(40) + "\n";
        LoadedDocument document = new LoadedDocument("doc001", "员工手册", "doc001_员工手册.md", body);

        List<Evidence> chunks = chunker.chunk(document);
        assertTrue(chunks.size() > 1, "超长段落应被拆成多个片段");
        for (int index = 0; index < chunks.size() - 1; index++) {
            String content = chunks.get(index).getContent();
            assertTrue(content.length() <= 400 + 40, "片段长度超过 max+overlap：" + content.length());
            char last = content.charAt(content.length() - 1);
            assertTrue(last == '。' || last == '！' || last == '？' || last == '；',
                    "非末尾片段应在句末标点处结束，实际以「" + last + "」结尾：" + content);
        }
    }

    @Test
    void firstChunkOfSectionHasNoOverlap() {
        properties.getRetrieval().setChunkMinChars(200);
        properties.getRetrieval().setChunkMaxChars(400);
        properties.getRetrieval().setChunkOverlapChars(40);
        String first = "甲".repeat(50);
        String second = "乙".repeat(60);
        String body = "## 甲章\n\n" + first + "\n\n## 乙章\n\n" + second + "\n";
        LoadedDocument document = new LoadedDocument("doc009", "测试制度", "doc009_测试制度.md", body);

        List<Evidence> chunks = chunker.chunk(document);
        assertEquals(2, chunks.size());
        assertEquals(first, chunks.get(0).getContent());
        assertEquals(second, chunks.get(1).getContent());
        assertFalse(chunks.get(1).getContent().contains("甲"), "章节首个片段不应包含上一章节的内容");
    }

    @Test
    void numbersChunksSequentiallyPerDocument() {
        String body = "## 总则\n\n" + "员工应遵守公司各项规定。".repeat(15) + "\n";
        LoadedDocument document = new LoadedDocument("doc008", "内部操作规范", "doc008_内部操作规范.md", body);

        List<Evidence> chunks = chunker.chunk(document);
        for (int index = 0; index < chunks.size(); index++) {
            assertEquals(String.format("doc008_chunk%02d", index + 1), chunks.get(index).getEvidenceId());
        }
    }
}
