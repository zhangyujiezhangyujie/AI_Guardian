package com.aiguardian.controller;

import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.infrastructure.lucene.KnowledgeBaseIndex;
import com.aiguardian.infrastructure.lucene.KnowledgeDocument;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库接口（契约 7.3、7.4、7.5）。
 *
 * <p>只读，数据全部来自启动时构建的内存索引；本控制器不做任何检索或判断。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Profile("!stub")
public class KnowledgeController {

    private final KnowledgeBaseIndex index;

    /** 7.3 文档列表，按 documentId 升序。 */
    @GetMapping("/documents")
    public List<DocumentSummary> documents() {
        return index.documents().stream()
                .map(document -> new DocumentSummary(document.documentId(), document.title(), document.fileName(),
                        document.chunks().size(), document.charCount()))
                .toList();
    }

    /** 7.4 文档详情：原文 + 全部片段。 */
    @GetMapping("/documents/{documentId}")
    public DocumentDetail document(@PathVariable String documentId) {
        KnowledgeDocument document = index.document(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        List<ChunkView> chunks = document.chunks().stream()
                .map(chunk -> new ChunkView(chunk.getEvidenceId(), chunk.getSection(), chunk.getContent()))
                .toList();
        return new DocumentDetail(document.documentId(), document.title(), document.content(), chunks);
    }

    /** 7.5 单个片段，score 与 rank 固定为 0。 */
    @GetMapping("/evidence/{evidenceId}")
    public Evidence evidence(@PathVariable String evidenceId) {
        return index.findById(evidenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));
    }

    public record DocumentSummary(String documentId, String title, String fileName, int chunkCount, int charCount) {
    }

    public record ChunkView(String evidenceId, String section, String content) {
    }

    public record DocumentDetail(String documentId, String title, String content, List<ChunkView> chunks) {
    }
}
