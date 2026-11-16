package com.aiguardian.infrastructure.lucene;

import com.aiguardian.domain.model.Evidence;
import java.util.List;

/**
 * 一篇知识库文档及其切分结果，供知识库接口（契约 7.3、7.4）使用。
 *
 * @param body 去掉标题行后的正文
 * @param chunks 按文档顺序排列的片段
 */
public record KnowledgeDocument(String documentId, String title, String fileName, String body, List<Evidence> chunks) {

    /** 完整原文：标题行 + 正文，与磁盘文件内容一致（已统一为 LF、无 BOM）。 */
    public String content() {
        return "# " + title + "\n" + body;
    }

    public int charCount() {
        return content().length();
    }
}
