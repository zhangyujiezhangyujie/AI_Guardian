package com.aiguardian.infrastructure.lucene;

/**
 * 从 knowledge-base 目录加载到的一篇文档。
 *
 * @param body 去掉第一行 {@code # 标题} 之后的正文，保留原有空行
 */
public record LoadedDocument(String documentId, String title, String fileName, String body) {
}
