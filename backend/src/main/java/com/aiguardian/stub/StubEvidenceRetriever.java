package com.aiguardian.stub;

import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.Evidence;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("stub")
public class StubEvidenceRetriever implements EvidenceRetriever {
    @Override
    public List<Evidence> retrieve(String query, int topK) {
        if (query.contains("食堂")) {
            return List.of();
        }
        return fixtures().stream().limit(topK).toList();
    }

    @Override
    public Optional<Evidence> findById(String evidenceId) {
        return fixtures().stream().filter(chunk -> chunk.getEvidenceId().equals(evidenceId)).findFirst();
    }

    private List<Evidence> fixtures() {
        return List.of(
                Evidence.builder().evidenceId("doc001_chunk03").documentId("doc001").documentTitle("员工手册")
                        .section("年假制度").content("正式员工每年享有15天带薪年假，试用期员工不享有年假。")
                        .score(4.52).rank(1).build(),
                Evidence.builder().evidenceId("doc005_chunk02").documentId("doc005").documentTitle("福利规定")
                        .section("餐费补贴").content("正式员工可按规定申请工作日餐费补贴。")
                        .score(2.35).rank(2).build());
    }
}
