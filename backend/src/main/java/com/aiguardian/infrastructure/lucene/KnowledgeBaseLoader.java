package com.aiguardian.infrastructure.lucene;

import com.aiguardian.config.AiGuardianProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 加载知识库文档（契约 6.1）。
 *
 * <p>只加载文件名以 {@code doc} 开头、以 {@code .md} 结尾的文件，因此同目录下的
 * {@code FACTS.md} 会被跳过。第一行必须是 {@code # 标题}，其余内容原样作为正文。
 */
@Component
public class KnowledgeBaseLoader {

    private final AiGuardianProperties properties;

    public KnowledgeBaseLoader(AiGuardianProperties properties) {
        this.properties = properties;
    }

    /** 配置项 {@code aiguardian.retrieval.knowledge-base-dir} 指向的绝对路径。 */
    public Path directory() {
        return Path.of(properties.getRetrieval().getKnowledgeBaseDir()).toAbsolutePath().normalize();
    }

    public List<LoadedDocument> loadAll() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("知识库目录不存在：" + directory);
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("doc") && name.endsWith(".md");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("无法读取知识库目录：" + directory, e);
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("知识库目录下没有 doc*.md 文件：" + directory);
        }
        List<LoadedDocument> documents = new ArrayList<>(files.size());
        for (Path file : files) {
            documents.add(load(file));
        }
        return documents;
    }

    private LoadedDocument load(Path file) {
        String fileName = file.getFileName().toString();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取知识库文档：" + file, e);
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        if (!firstLine.startsWith("# ")) {
            throw new IllegalStateException("文档第一行必须是 \"# 标题\"：" + fileName);
        }
        String title = firstLine.substring(2).trim();
        String body = firstLineEnd < 0 ? "" : text.substring(firstLineEnd + 1);

        int separator = fileName.indexOf('_');
        String documentId = separator > 0 ? fileName.substring(0, separator) : fileName.replace(".md", "");
        return new LoadedDocument(documentId, title, fileName, body);
    }
}
