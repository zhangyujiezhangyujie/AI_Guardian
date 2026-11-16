package com.aiguardian.infrastructure.lucene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;

/**
 * 阶段一验证：中文分词与 BM25 最小闭环（任务卡 5.阶段一第 3、4 条）。
 *
 * <p>只验证三件事：
 * <ol>
 *   <li>SmartChineseAnalyzer 能把"年假""带薪年假"切分出来；</li>
 *   <li>"员工一年有多少天年假"能召回年假片段并排在前面；</li>
 *   <li>"公司食堂周末几点开门？"当前的召回情况，为阶段二 min-score 调参提供基线。</li>
 * </ol>
 *
 * <p>这里的切分是临时实现（按 ## 章节切、段落累加到 380 字），仅用于本验证。
 * 正式切分与检索见阶段二的 Chunker 与 LuceneEvidenceRetriever，本类在那之后可以删除。
 */
class LuceneTokenizerDemoTest {

    private static final int DEMO_MAX_CHARS = 380;
    private static final List<String> TERMS = List.of(
            "年假", "带薪年假", "年休假", "请假", "事假", "病假", "婚假",
            "报销", "住宿", "出差", "保修", "退货", "迟到", "旷工",
            "餐费补贴", "福利", "体检", "审批", "删除", "内网");
    private static final List<String> QUESTIONS = List.of(
            "员工一年有多少天年假",
            "年假有几天？",
            "内网能看到客户数据吗？",
            "公司食堂周末几点开门？");

    @Test
    void printsTokensAndTopThree() throws Exception {
        Path knowledgeBase = locateKnowledgeBase();
        List<Chunk> chunks = loadChunks(knowledgeBase);
        assertFalse(chunks.isEmpty(), "知识库为空：" + knowledgeBase);

        StringBuilder report = new StringBuilder();
        report.append("知识库目录：").append(knowledgeBase.toAbsolutePath()).append('\n');
        report.append("文档数：").append(chunks.stream().map(Chunk::documentId).distinct().count())
                .append("，片段数：").append(chunks.size()).append('\n').append('\n');

        try (Analyzer analyzer = new SmartChineseAnalyzer();
             Directory directory = new ByteBuffersDirectory()) {
            buildIndex(directory, analyzer, chunks);

            report.append("== 分词结果 ==\n");
            List<String> questionTokens = tokens(analyzer, QUESTIONS.get(0));
            List<String> sentenceTokens = tokens(analyzer, "正式员工每年享有15天带薪年假，试用期员工不享有年假。");
            report.append("问题「").append(QUESTIONS.get(0)).append("」→ ").append(questionTokens).append('\n');
            report.append("句子「正式员工每年享有15天带薪年假」→ ").append(sentenceTokens).append('\n');
            report.append('\n');

            report.append("== 关键词切分 ==\n");
            for (String term : TERMS) {
                report.append(term).append(" → ").append(tokens(analyzer, term)).append('\n');
            }
            report.append('\n');

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(new BM25Similarity());
                QueryParser parser = new QueryParser("searchText", analyzer);

                for (String question : QUESTIONS) {
                    report.append("== Top 3：").append(question).append(" ==\n");
                    Query query = parser.parse(QueryParser.escape(question));
                    TopDocs topDocs = searcher.search(query, 3);
                    report.append("命中总数：").append(topDocs.totalHits.value).append('\n');
                    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                        Document doc = searcher.doc(scoreDoc.doc);
                        report.append(String.format(Locale.ROOT, "  %.4f  %s  %s · %s  %s%n",
                                scoreDoc.score,
                                doc.get("evidenceId"),
                                doc.get("documentTitle"),
                                doc.get("section"),
                                abbreviate(doc.get("content"), 40)));
                    }
                    report.append('\n');
                }
            }

            System.out.println(report);
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target", "lucene-demo.txt"), report.toString(), StandardCharsets.UTF_8);

            assertTrue(report.indexOf("员工手册 · 年假制度") > 0,
                    "年假问题没有召回《员工手册·年假制度》片段：\n" + report);
        }
    }

    private static Path locateKnowledgeBase() {
        Path fromBackend = Path.of("..", "knowledge-base");
        if (Files.isDirectory(fromBackend)) {
            return fromBackend;
        }
        Path fromRoot = Path.of("knowledge-base");
        assertTrue(Files.isDirectory(fromRoot), "找不到知识库目录，工作目录为 " + Path.of("").toAbsolutePath());
        return fromRoot;
    }

    private static List<Chunk> loadChunks(Path directory) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().startsWith("doc"))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        List<Chunk> chunks = new ArrayList<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String documentId = fileName.substring(0, fileName.indexOf('_'));
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String title = lines.get(0).replaceFirst("^#\\s*", "").trim();
            String section = null;
            StringBuilder buffer = new StringBuilder();
            List<Chunk> documentChunks = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                String trimmed = line.trim();
                if (trimmed.startsWith("## ")) {
                    flush(documentChunks, documentId, title, section, buffer);
                    section = trimmed.substring(3).trim();
                    continue;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(trimmed);
                if (buffer.length() >= DEMO_MAX_CHARS) {
                    flush(documentChunks, documentId, title, section, buffer);
                }
            }
            flush(documentChunks, documentId, title, section, buffer);
            for (int index = 0; index < documentChunks.size(); index++) {
                Chunk chunk = documentChunks.get(index);
                chunk.evidenceId = String.format("%s_chunk%02d", documentId, index + 1);
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private static void flush(List<Chunk> target, String documentId, String title, String section,
                              StringBuilder buffer) {
        if (buffer.length() == 0) {
            return;
        }
        target.add(new Chunk(documentId, title, section, buffer.toString()));
        buffer.setLength(0);
    }

    private static void buildIndex(Directory directory, Analyzer analyzer, List<Chunk> chunks) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity());
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Chunk chunk : chunks) {
                Document document = new Document();
                document.add(new StringField("evidenceId", chunk.evidenceId, Field.Store.YES));
                document.add(new StringField("documentId", chunk.documentId, Field.Store.YES));
                document.add(new StoredField("documentTitle", chunk.documentTitle));
                if (chunk.section != null) {
                    document.add(new StoredField("section", chunk.section));
                }
                document.add(new StoredField("content", chunk.content));
                document.add(new TextField("searchText",
                        chunk.documentTitle + " " + (chunk.section == null ? "" : chunk.section) + " " + chunk.content,
                        Field.Store.NO));
                writer.addDocument(document);
            }
        }
    }

    private static List<String> tokens(Analyzer analyzer, String text) throws IOException {
        List<String> result = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("searchText", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                result.add(term.toString());
            }
            stream.end();
        }
        return result;
    }

    private static String abbreviate(String text, int max) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private static final class Chunk {
        private final String documentId;
        private final String documentTitle;
        private final String section;
        private final String content;
        private String evidenceId;

        private Chunk(String documentId, String documentTitle, String section, String content) {
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.section = section;
            this.content = content;
        }

        private String documentId() {
            return documentId;
        }
    }
}
