package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 每个片段的 content 必须是原文件文本（去掉标题行）的子串，否则成员 3 的引用校验会失败。
 */
class EvidenceContentTest {

    @Test
    void everyChunkContentIsSubstringOfSourceFile() throws Exception {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(properties);
        Chunker chunker = new Chunker(properties);

        List<LoadedDocument> documents = loader.loadAll();
        assertFalse(documents.isEmpty());

        int total = 0;
        for (LoadedDocument document : documents) {
            String source = Files.readString(loader.directory().resolve(document.fileName()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            String body = source.substring(source.indexOf('\n') + 1);
            List<Evidence> chunks = chunker.chunk(document);
            assertFalse(chunks.isEmpty(), "文档没有切出片段：" + document.fileName());
            for (Evidence chunk : chunks) {
                assertTrue(body.contains(chunk.getContent()),
                        chunk.getEvidenceId() + " 的 content 不是原文子串");
                assertFalse(chunk.getContent().isBlank(), chunk.getEvidenceId() + " 的 content 为空");
                assertTrue(chunk.getContent().length() <= properties.getRetrieval().getChunkMaxChars()
                                + properties.getRetrieval().getChunkOverlapChars(),
                        chunk.getEvidenceId() + " 长度超过 max+overlap：" + chunk.getContent().length());
                total++;
            }
        }
        assertTrue(total >= 8, "片段总数异常：" + total);
    }

    @Test
    void chunksDoNotIncludeSectionHeadings() {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(properties);
        Chunker chunker = new Chunker(properties);

        for (LoadedDocument document : loader.loadAll()) {
            for (Evidence chunk : chunker.chunk(document)) {
                assertFalse(chunk.getContent().contains("## "),
                        chunk.getEvidenceId() + " 的 content 包含章节标题行");
                assertFalse(chunk.getContent().startsWith("#"),
                        chunk.getEvidenceId() + " 的 content 以标题开头");
            }
        }
    }

    @Test
    void documentsDirectoryPointsToKnowledgeBase() {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(properties);
        Path directory = loader.directory();
        assertTrue(Files.isDirectory(directory), "找不到知识库目录：" + directory);
        assertTrue(Files.isRegularFile(directory.resolve("FACTS.md")), "FACTS.md 应位于知识库目录");
        assertTrue(loader.loadAll().stream().noneMatch(document -> document.fileName().equals("FACTS.md")),
                "加载器不应加载 FACTS.md");
    }
}
