package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LuceneEvidenceRetrieverTest {

    private AiGuardianProperties properties;
    private KnowledgeBaseIndex index;
    private LuceneEvidenceRetriever retriever;

    @BeforeEach
    void setUp() {
        properties = new AiGuardianProperties();
        properties.getRetrieval().setKnowledgeBaseDir("../knowledge-base");
        properties.getRetrieval().setTopK(3);
        index = new KnowledgeBaseIndex(properties, new KnowledgeBaseLoader(properties), new Chunker(properties));
        index.build();
        retriever = new LuceneEvidenceRetriever(index);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void findsAnnualLeaveChunkInTopThree() {
        List<Evidence> evidence = retriever.retrieve("员工一年有多少天年假", 3);
        assertFalse(evidence.isEmpty(), "年假问题应有检索结果");
        assertTrue(evidence.stream().anyMatch(item -> "年假制度".equals(item.getSection())),
                "Top 3 应包含年假制度片段，实际：" + evidence.stream().map(Evidence::getEvidenceId).toList());
    }

    @Test
    void sortsByScoreDescendingAndNumbersRanksFromOne() {
        List<Evidence> evidence = retriever.retrieve("员工一年有多少天年假", 3);
        for (int position = 0; position < evidence.size(); position++) {
            assertEquals(position + 1, evidence.get(position).getRank());
            if (position > 0) {
                assertTrue(evidence.get(position - 1).getScore() >= evidence.get(position).getScore(),
                        "结果没有按分数降序排列");
            }
        }
    }

    @Test
    void blankQueryReturnsEmptyList() {
        assertTrue(retriever.retrieve("", 3).isEmpty());
        assertTrue(retriever.retrieve("   ", 3).isEmpty());
        assertTrue(retriever.retrieve(null, 3).isEmpty());
    }

    @Test
    void specialCharactersDoNotThrow() {
        assertDoesNotThrowQuery("年假?*");
        assertDoesNotThrowQuery("年假\"OR\"");
        assertDoesNotThrowQuery("+ - && || ! ( ) { } [ ] ^ ~ : \\ /");
    }

    @Test
    void minScoreDropsEverythingWhenSetTooHigh() {
        properties.getRetrieval().setMinScore(100);
        assertTrue(retriever.retrieve("员工一年有多少天年假", 3).isEmpty());
    }

    @Test
    void tunedThresholdMakesCanteenQuestionEmptyButKeepsRealQuestions() {
        properties.getRetrieval().setMinScore(1.0);
        assertTrue(retriever.retrieve("公司食堂周末几点开门？", 3).isEmpty(),
                "SC07 在 min-score=1.0 时应检索为空");
        for (String question : List.of("公司正式员工一年有多少天年假？", "员工请假需要提前几天申请？",
                "产品的保修期是多久？", "员工迟到如何处理？", "报销需要提交哪些材料？")) {
            assertFalse(retriever.retrieve(question, 3).isEmpty(), "正常问题不应被阈值压掉：" + question);
        }
    }

    @Test
    void findByIdReturnsStoredEvidence() {
        Evidence chunk = index.documents().get(0).chunks().get(0);
        assertTrue(retriever.findById(chunk.getEvidenceId()).isPresent());
        assertEquals(chunk.getContent(), retriever.findById(chunk.getEvidenceId()).orElseThrow().getContent());
        assertTrue(retriever.findById("doc999_chunk01").isEmpty());
        assertTrue(retriever.findById(null).isEmpty());
    }

    @Test
    void canteenQuestionScoresLowerThanAnnualLeaveQuestion() {
        double canteen = maxScore(retriever.retrieve("公司食堂周末几点开门？", 3));
        double annualLeave = maxScore(retriever.retrieve("员工一年有多少天年假", 3));
        assertTrue(canteen < annualLeave,
                "食堂问题的最高分应低于年假问题，实际 " + canteen + " vs " + annualLeave);
    }

    private static double maxScore(List<Evidence> evidence) {
        return evidence.stream().mapToDouble(Evidence::getScore).max().orElse(0);
    }

    private void assertDoesNotThrowQuery(String query) {
        List<Evidence> evidence = retriever.retrieve(query, 3);
        assertTrue(evidence.size() <= 3);
    }
}
