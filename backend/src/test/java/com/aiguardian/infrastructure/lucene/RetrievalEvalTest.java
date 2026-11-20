package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * 评测使用生产配置（{@code topK=3}、{@code min-score}、停用词均取自 application.yml），
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

        int topK = properties.getRetrieval().getTopK();
        StringBuilder report = new StringBuilder();
        report.append("# 检索 Top 1 / Top 3 命中率评测\n\n");
        report.append("- 知识库片段数：").append(index.documents().stream()
                .mapToInt(document -> document.chunks().size()).sum()).append('\n');
        report.append("- topK：").append(topK)
                .append("；min-score：").append(properties.getRetrieval().getMinScore())
                .append("；停用词：").append(properties.getRetrieval().getStopwords()).append("\n\n");
        report.append("| # | 问题 | 标注正确片段 | 实际 Top 1 | Top 1 命中 | Top 3 命中 |\n");
        report.append("| ---: | --- | --- | --- | --- | --- |\n");

        int topOneHits = 0;
        int topThreeHits = 0;
        try {
            for (int position = 0; position < CASES.size(); position++) {
                Case testCase = CASES.get(position);
                List<Evidence> evidence = index.search(testCase.question(), topK);
                List<String> actualIds = evidence.stream().map(Evidence::getEvidenceId).toList();
                boolean topOne = !actualIds.isEmpty() && testCase.expectedIds().contains(actualIds.get(0));
                boolean topThree = actualIds.stream().anyMatch(testCase.expectedIds()::contains);
                topOneHits += topOne ? 1 : 0;
                topThreeHits += topThree ? 1 : 0;

                report.append(String.format("| %d | %s | %s | %s | %s | %s |%n",
                        position + 1, testCase.question(), String.join("、", testCase.expectedIds()),
                        actualIds.isEmpty() ? "（空）" : describe(evidence.get(0)),
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

    private static String describe(Evidence evidence) {
        return evidence.getEvidenceId() + "（" + evidence.getSection() + "，"
                + String.format(java.util.Locale.ROOT, "%.2f", evidence.getScore()) + "）";
    }

    private record Case(String question, List<String> expectedIds) {
    }
}
