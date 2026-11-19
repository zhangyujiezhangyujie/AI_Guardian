package com.aiguardian.infrastructure.lucene;

import com.aiguardian.common.BusinessException;
import com.aiguardian.common.ErrorCode;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.Evidence;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 基于内存 Lucene 索引的证据检索实现（契约 4.2）。
 *
 * <p>只负责文本相关性，不做任何“是否可信”的判断；score 是 BM25 分数，不是可信度。
 */
@Component
@Profile("!stub")
public class LuceneEvidenceRetriever implements EvidenceRetriever {

    private final KnowledgeBaseIndex index;

    public LuceneEvidenceRetriever(KnowledgeBaseIndex index) {
        this.index = index;
    }

    @Override
    public List<Evidence> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            return index.search(query, topK);
        } catch (ParseException e) {
            // 查询本身无法解析时按“没有检索结果”处理，不把异常抛给调用方。
            return List.of();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RETRIEVAL_FAILED);
        }
    }

    @Override
    public Optional<Evidence> findById(String evidenceId) {
        return index.findById(evidenceId);
    }
}
