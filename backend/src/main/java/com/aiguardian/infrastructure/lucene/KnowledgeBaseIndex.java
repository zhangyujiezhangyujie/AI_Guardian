package com.aiguardian.infrastructure.lucene;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.Evidence;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存 Lucene 索引（契约 6.5）：应用启动时用 {@link ByteBuffersDirectory} 构建，
 * 索引与查询共用同一个 {@link SmartChineseAnalyzer}，相似度显式使用 {@link BM25Similarity}。
 */
@Component
@Profile("!stub")
public class KnowledgeBaseIndex {

    /**
     * 停用词（调参结论，见 docs/experiments/retrieval_tuning.md）。
     * 只停“公司”：它几乎出现在每份文档里，没有区分度，却让“公司食堂周末几点开门？”
     * 之类的无证据问题也能召回一堆无关片段。契约第 11 章没有 stopwords 配置键，
     * 暂以常量实现，待成员 1 确认后再迁移到配置。
     */
    static final Set<String> STOPWORDS = Set.of("公司");

    private static final String FIELD_SEARCH = "searchText";

    private final AiGuardianProperties properties;
    private final KnowledgeBaseLoader loader;
    private final Chunker chunker;
    private final Directory directory = new ByteBuffersDirectory();
    private final Map<String, Evidence> byId = new LinkedHashMap<>();
    private final List<KnowledgeDocument> documents = new ArrayList<>();

    private Analyzer analyzer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    public KnowledgeBaseIndex(AiGuardianProperties properties, KnowledgeBaseLoader loader, Chunker chunker) {
        this.properties = properties;
        this.loader = loader;
        this.chunker = chunker;
    }

    @PostConstruct
    public void build() {
        List<LoadedDocument> loaded = loader.loadAll();
        List<KnowledgeDocument> built = new ArrayList<>(loaded.size());
        List<Evidence> chunks = new ArrayList<>();
        for (LoadedDocument document : loaded) {
            List<Evidence> documentChunks = chunker.chunk(document);
            chunks.addAll(documentChunks);
            built.add(new KnowledgeDocument(document.documentId(), document.title(), document.fileName(),
                    document.body(), List.copyOf(documentChunks)));
        }
        built.sort(Comparator.comparing(KnowledgeDocument::documentId));

        analyzer = createAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity());
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Evidence chunk : chunks) {
                writer.addDocument(toDocument(chunk));
            }
        } catch (IOException e) {
            throw new IllegalStateException("构建 Lucene 索引失败：" + loader.directory(), e);
        }

        try {
            reader = DirectoryReader.open(directory);
        } catch (IOException e) {
            throw new IllegalStateException("打开 Lucene 索引失败：" + loader.directory(), e);
        }
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        for (KnowledgeDocument document : built) {
            for (Evidence chunk : document.chunks()) {
                byId.put(chunk.getEvidenceId(), chunk);
            }
        }
        documents.clear();
        documents.addAll(built);
    }

    /** 索引与查询共用同一个分析器实例；单独抽出便于调参实验替换停用词集合。 */
    protected Analyzer createAnalyzer() {
        return new SmartChineseAnalyzer(new CharArraySet(STOPWORDS, true));
    }

    /** 按 BM25 分数降序返回最多 topK 条，丢弃低于 {@code min-score} 的结果。 */
    public List<Evidence> search(String query, int topK) throws IOException, ParseException {
        if (searcher == null) {
            throw new IllegalStateException("索引尚未构建");
        }
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        QueryParser parser = new QueryParser(FIELD_SEARCH, analyzer);
        Query parsed = parser.parse(QueryParser.escape(query));
        TopDocs topDocs = searcher.search(parsed, topK);
        double minScore = properties.getRetrieval().getMinScore();
        List<Evidence> result = new ArrayList<>(topDocs.scoreDocs.length);
        int rank = 1;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            if (scoreDoc.score < minScore) {
                continue;
            }
            Document document = searcher.doc(scoreDoc.doc);
            result.add(Evidence.builder()
                    .evidenceId(document.get("evidenceId"))
                    .documentId(document.get("documentId"))
                    .documentTitle(document.get("documentTitle"))
                    .section(document.get("section"))
                    .content(document.get("content"))
                    .score(scoreDoc.score)
                    .rank(rank++)
                    .build());
        }
        return result;
    }

    public Optional<Evidence> findById(String evidenceId) {
        return evidenceId == null ? Optional.empty() : Optional.ofNullable(byId.get(evidenceId));
    }

    public List<KnowledgeDocument> documents() {
        return List.copyOf(documents);
    }

    public Optional<KnowledgeDocument> document(String documentId) {
        return documentId == null ? Optional.empty()
                : documents.stream().filter(document -> document.documentId().equals(documentId)).findFirst();
    }

    @PreDestroy
    public void close() {
        closeQuietly(reader);
        closeQuietly(analyzer);
        closeQuietly(directory);
    }

    private Document toDocument(Evidence chunk) {
        Document document = new Document();
        document.add(new StringField("evidenceId", chunk.getEvidenceId(), Field.Store.YES));
        document.add(new StringField("documentId", chunk.getDocumentId(), Field.Store.YES));
        document.add(new StoredField("documentTitle", chunk.getDocumentTitle()));
        if (chunk.getSection() != null) {
            document.add(new StoredField("section", chunk.getSection()));
        }
        document.add(new StoredField("content", chunk.getContent()));
        String searchText = chunk.getDocumentTitle() + " "
                + (chunk.getSection() == null ? "" : chunk.getSection()) + " "
                + chunk.getContent();
        document.add(new TextField(FIELD_SEARCH, searchText, Field.Store.NO));
        return document;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 应用关闭阶段忽略关闭异常
        }
    }
}
