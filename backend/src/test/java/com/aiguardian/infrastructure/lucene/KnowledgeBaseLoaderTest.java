package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeBaseLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsOnlyDocMarkdownFilesAndSkipsFacts() throws Exception {
        Files.writeString(tempDir.resolve("doc001_员工手册.md"), "# 员工手册\n\n## 总则\n\n内容。\n");
        Files.writeString(tempDir.resolve("FACTS.md"), "| 编号 | 事实 |\n| --- | --- |\n");
        Files.writeString(tempDir.resolve("notes.txt"), "忽略我");
        Files.writeString(tempDir.resolve("README.md"), "# 说明\n");

        List<LoadedDocument> documents = loader(tempDir).loadAll();

        assertEquals(1, documents.size());
        assertEquals("doc001", documents.get(0).documentId());
        assertEquals("员工手册", documents.get(0).title());
        assertEquals("doc001_员工手册.md", documents.get(0).fileName());
        assertTrue(documents.get(0).body().contains("内容。"));
    }

    @Test
    void failsFastWhenDirectoryMissing() {
        KnowledgeBaseLoader loader = loader(tempDir.resolve("不存在"));
        IllegalStateException error = assertThrows(IllegalStateException.class, loader::loadAll);
        assertTrue(error.getMessage().contains("知识库目录不存在"));
    }

    @Test
    void failsWhenFirstLineIsNotTitle() throws Exception {
        Files.writeString(tempDir.resolve("doc002_请假制度.md"), "请假制度\n\n## 总则\n\n内容。\n");
        assertThrows(IllegalStateException.class, loader(tempDir)::loadAll);
    }

    @Test
    void stripsBomAndNormalizesCrlf() throws Exception {
        Files.writeString(tempDir.resolve("doc003_报销制度.md"), "\uFEFF# 报销制度\r\n\r\n## 材料\r\n\r\n发票。\r\n");

        LoadedDocument document = loader(tempDir).loadAll().get(0);

        assertEquals("报销制度", document.title());
        assertFalse(document.body().contains("\r"), "正文不应保留 CR");
        assertEquals("\n## 材料\n\n发票。\n", document.body());
    }

    private KnowledgeBaseLoader loader(Path directory) {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir(directory.toString());
        return new KnowledgeBaseLoader(properties);
    }
}
