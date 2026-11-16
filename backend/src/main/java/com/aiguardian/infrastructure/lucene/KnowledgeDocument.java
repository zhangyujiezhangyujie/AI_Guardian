package com.aiguardian.infrastructure.lucene;

import com.aiguardian.domain.model.Evidence;
import java.util.List;

/**
 * 一篇知识库文档及其切分结果，供知识库接口（契约 7.3、7.4）使用。
 *
 * @param body   去掉标题行后的正文
 * @param chunks 按文档顺序排列的片段
 */
public record KnowledgeDocument(String documentId, String title, String fileName, String body, List<Evidence> chunks) {

    public int charCount() {
        return body.length();
    }
}
