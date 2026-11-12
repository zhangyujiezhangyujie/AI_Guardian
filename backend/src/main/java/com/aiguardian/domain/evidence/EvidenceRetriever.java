package com.aiguardian.domain.evidence;

import com.aiguardian.domain.model.Evidence;
import java.util.List;
import java.util.Optional;

public interface EvidenceRetriever {
    /** 按相关性降序返回最多 topK 个片段，无命中时返回空列表。 */
    List<Evidence> retrieve(String query, int topK);

    Optional<Evidence> findById(String evidenceId);
}
