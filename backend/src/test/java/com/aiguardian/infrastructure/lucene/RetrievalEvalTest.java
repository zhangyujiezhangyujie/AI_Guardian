package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 阶段四：10 条口语化问题的 Top 1 / Top 3 命中率评测（离线，不联网）。
 *
 * <p>问题由成员 2 设计并人工标注正确片段，问题不与 8 个固定场景重复。
 * 评测使用生产配置（topK、min-score、停用词、切分参数均取自 application.yml），
 * 因此结果就是系统当前对员工自然提问的实际召回表现。
 *
 * <p>输出写入 {@code target/retrieval-eval.md}，同时打印到控制台；结果记录见
 * {@code docs/experiments/retrieval_eval.md}。
 */
class RetrievalEvalTest {

    /** 问题与人工标注的正确片段 ID（一个或多个）。 */
    private static final List<Case> CASES = List.of(
            new Case("休年假的话一年能休几天？", List.of("doc001_chunk02")),
            new Case("刚入职还在试用期，能休年假吗？", List.of("doc001_chunk02")),
            new Case("出差住酒店一晚最多能报多少？", List.of("doc003_chunk02")),
            new Case("报销单最晚什么时候交？", List.of("doc003_chunk02")),
            new Case("迟到了会扣多少钱？", List.of("doc004_chunk02")),
            new Case("婚假能休多少天？", List.of("doc002_chunk02")),
            new Case("请病假要交什么证明材料？", List.of("doc002_chunk02")),
            new Case("智能门锁哪款最便宜？", List.of("doc006_chunk01")),
            new Case("买的产品坏了，能保修多久？", List.of("doc007_chunk01")),
            new Case("客户资料能不能带回家看？", List.of("doc008_chunk01")));

    @Test
    void measuresTopOneAndTopThreeHitRate() throws Exception {
        AiGuardianProperties properties = new AiGuardianProperties();
        var source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).get(0);
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(source);
        Binder.get(environment).bind("aiguardian.retrieval", Bindable.ofInstance(properties.getRetrieval()));

        KnowledgeBaseIndex index = new KnowledgeBaseIndex(properties, new KnowledgeBaseLoader(properties),
                new Chunker(properties));
        index.build();

        var retrieval = properties.getRetrieval();
        StringBuilder report = new StringBuilder();
        report.append("# 检索 Top 1 / Top 3 命中率评测\n\n");
        report.append("- 知识库：8 篇文档，").append(index.documents().stream()
                .mapToInt(document -> document.chunks().size()).sum()).append(" 个片段\n");
        report.append("- 切分参数：chunk-min-chars=").append(retrieval.getChunkMinChars())
                .append("，chunk-max-chars=").append(retrieval.getChunkMaxChars())
                .append("，chunk-overlap-chars=").append(retrieval.getChunkOverlapChars()).append('\n');
        report.append("- 检索参数：topK=").append(retrieval.getTopK())
                .append("，min-score=").append(retrieval.getMinScore())
                .append("，stopwords=").append(retrieval.getStopwords()).append("\n\n");
        report.append("| # | 问题 | 预期片段（文档·章节） | Top 1 | Top 2 | Top 3 | Top 1 命中 | Top 3 命中 |\n");
        report.append("| ---: | --- | --- | --- | --- | --- | --- | --- |\n");

        int topOneHits = 0;
        int topThreeHits = 0;
        try {
            for (int position = 0; position < CASES.size(); position++) {
                Case testCase = CASES.get(position);
                List<Evidence> evidence = index.search(testCase.question(), retrieval.getTopK());
                List<String> actualIds = evidence.stream().map(Evidence::getEvidenceId).toList();
                boolean topOne = !actualIds.isEmpty() && testCase.expectedIds().contains(actualIds.get(0));
                boolean topThree = actualIds.stream().anyMatch(testCase.expectedIds()::contains);
                topOneHits += topOne ? 1 : 0;
                topThreeHits += topThree ? 1 : 0;

                report.append(String.format("| %d | %s | %s | %s | %s | %s | %s | %s |%n",
                        position + 1, testCase.question(), describeExpected(index, testCase.expectedIds()),
                        describe(evidence, 0), describe(evidence, 1), describe(evidence, 2),
                        topOne ? "是" : "**否**", topThree ? "是" : "**否**"));
            }
        } finally {
            index.close();
        }

        double topOneRate = topOneHits / (double) CASES.size();
        double topThreeRate = topThreeHits / (double) CASES.size();
        report.append(String.format("%n- Top 1 命中：%d/%d = %.0f%%%n", topOneHits, CASES.size(), topOneRate * 100));
        report.append(String.format("- Top 3 命中：%d/%d = %.0f%%%n", topThreeHits, CASES.size(), topThreeRate * 100));

        System.out.println(report);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "retrieval-eval.md"), report.toString(), StandardCharsets.UTF_8);

        assertTrue(topOneRate >= 0.6, "Top 1 命中率低于 60%：" + topOneRate);
        assertTrue(topThreeRate >= 0.7, "Top 3 命中率低于 70%：" + topThreeRate);
    }

    /** 把标注的片段 ID 渲染成“ID（文档·章节）”，便于报告直接引用。 */
    private static String describeExpected(KnowledgeBaseIndex index, List<String> expectedIds) {
        return expectedIds.stream()
                .map(id -> index.findById(id)
                        .map(evidence -> id + "（" + evidence.getDocumentTitle() + "·" + evidence.getSection() + "）")
                        .orElse(id))
                .collect(Collectors.joining("、"));
    }

    private static String describe(List<Evidence> evidence, int position) {
        if (position >= evidence.size()) {
            return "（无）";
        }
        Evidence item = evidence.get(position);
        return item.getEvidenceId() + "（" + item.getSection() + "，"
                + String.format(Locale.ROOT, "%.2f", item.getScore()) + "）";
    }

    private record Case(String question, List<String> expectedIds) {
    }
}
