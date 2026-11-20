package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.lucene.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class OfficialEvidenceBindingTest {
    final ObjectMapper json = new ObjectMapper();
    final Path file = Path.of("../docs/verification/annotations-reviewed.json");
    AiGuardianProperties config() {
        var p = new AiGuardianProperties();
        p.getRetrieval().setMinScore(1.0);
        return p;
    }

    @Test void bindAndValidateOfficialChunks() throws Exception {
        var p = config();
        var loader = new KnowledgeBaseLoader(p);
        var chunker = new Chunker(p);
        var chunks = new ArrayList<Evidence>();
        var manifest = json.createArrayNode();
        var locations = json.createArrayNode();
        for (var doc : loader.loadAll()) {
            Path path = loader.directory().resolve(doc.fileName());
            manifest.addObject().put("path", "knowledge-base/" + doc.fileName())
                    .put("sha256", sha(Files.readAllBytes(path)));
            for (var e : chunker.chunk(doc)) {
                chunks.add(e);
                int start = doc.body().indexOf(e.getContent());
                assertTrue(start >= 0);
                locations.addObject().put("evidenceId", e.getEvidenceId())
                        .put("path", "knowledge-base/" + doc.fileName())
                        .put("bodyStartUtf16", start).put("bodyEndUtf16", start + e.getContent().length())
                        .put("contentSha256", sha(e.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        var data = (ObjectNode) json.readTree(file.toFile());
        if (Boolean.getBoolean("rebindOfficialEvidence")) {
            var oldById = new HashMap<String, String>();
            for (var s : data.get("sources")) oldById.put(s.get("evidenceId").asText(), s.get("documentId").asText());
            var changes = json.createArrayNode();
            for (var item : data.get("cases")) {
                var ids = new LinkedHashSet<String>();
                for (var c : item.get("claims")) {
                    var claim = (ObjectNode) c;
                    String oldId = c.get("reviewEvidenceId").asText();
                    String quote = c.get("reviewQuote").asText();
                    var source = chunks.stream().filter(e -> e.getDocumentId().equals(oldById.get(oldId))
                            && e.getContent().contains(quote)).findFirst().orElseThrow(() -> new AssertionError("需重新复核引文: " + c.get("id") + quote));
                    changes.addObject().put("claimId", c.get("id").asText()).put("oldEvidenceId", oldId)
                            .put("newEvidenceId", source.getEvidenceId()).put("labelChanged", false);
                    claim.put("reviewEvidenceId", source.getEvidenceId());
                    claim.put("reviewDate", "2026-11-16");
                    ids.add(source.getEvidenceId());
                    if (c.get("proposedStatus").asText().equals("SUPPORTED")) {
                        claim.put("referenceEvidenceId", source.getEvidenceId());
                        assertTrue(source.getContent().contains(c.get("referenceQuote").asText()));
                    }
                }
                // 适用范围一并绑定，防止只用金额或价格一句话丢失总则条件。
                for (var e : chunks) if (ids.stream().anyMatch(id -> id.startsWith(e.getDocumentId() + "_"))
                        && ("总则".equals(e.getSection()) || "产品线概览".equals(e.getSection()))) ids.add(e.getEvidenceId());
                ((ObjectNode) item).set("evidenceIds", json.valueToTree(ids));
            }
            data.set("sources", json.valueToTree(chunks));
            data.set("sourceManifest", manifest);
            data.set("evidenceManifest", locations);
            data.put("version", "1.1-ai-reviewed");
            data.put("reviewDate", "2026-11-16");
            data.put("knowledgeBaseCommit", "d331eb5");
            data.put("evidenceIdStatus", "OFFICIAL_CHUNKER_IDS");
            data.put("chunking", "正式KnowledgeBaseLoader和Chunker；min=200,max=400,overlap=40，保留空行");
            data.put("sourceIntegrationStatus", "正式知识库及正式切分器已对齐；AI标签复核，不冒充人工标注");
            data.put("retrieval", "标注集为固定证据，score/rank=0；真实BM25衔接另由集成测试验证。");
            json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
            json.writerWithDefaultPrettyPrinter().writeValue(Path.of("../docs/verification/evidence-rebinding.json").toFile(), changes);
        }
        assertEquals("OFFICIAL_CHUNKER_IDS", data.get("evidenceIdStatus").asText());
        assertEquals(json.valueToTree(chunks), data.get("sources"));
        assertEquals(manifest, data.get("sourceManifest"));
        assertEquals(locations, data.get("evidenceManifest"));
    }

    @ParameterizedTest @ValueSource(strings = {"batch", "per-claim"})
    void realRetrievalFeedsBothVerifiersAndCitationOffsets(String name) throws Exception {
        var p = config();
        var index = new KnowledgeBaseIndex(p, new KnowledgeBaseLoader(p), new Chunker(p));
        try {
            index.build();
            var retriever = new LuceneEvidenceRetriever(index);
            var evidence = retriever.retrieve("正式员工一年有多少天年假", 3);
            var source = evidence.stream().filter(e -> e.getContent().contains("正式员工每年享有15天带薪年假")).findFirst().orElseThrow();
            assertTrue(source.getScore() >= 1);
            assertTrue(source.getRank() > 0);
            var root = json.createObjectNode();
            var nodes = root.putArray("claims");
            nodes.addObject().put("claim", "年假15天").put("originalText", "年假15天").put("supported", true)
                    .put("evidenceId", source.getEvidenceId()).put("quote", "正式员工每年享有15天带薪年假");
            nodes.addObject().put("claim", "交通补贴2000元").put("originalText", "交通补贴2000元").put("supported", false)
                    .putNull("evidenceId").putNull("quote");
            com.aiguardian.infrastructure.llm.LLMClient model = r -> {
                try {
                    var target = json.readTree(r.getUserPrompt()).get("target");
                    if (target == null) return root.toString();
                    var single = json.createObjectNode();
                    var output = single.putArray("claims");
                    for (var node : nodes) if (node.get("claim").equals(target.get("claim"))) output.add(node);
                    return single.toString();
                } catch (Exception e) { throw new IllegalStateException(e); }
            };
            ClaimVerifier verifier = name.equals("batch") ? new LLMBatchClaimVerifier(model, json, p) : new PerClaimClaimVerifier(model, json, p);
            var report = verifier.verify(VerificationInput.builder().requestId("integration").mode(RunMode.REAL)
                    .question("年假几天？").answer("😀年假15天，交通补贴2000元").evidence(evidence).build());
            new ExactQuoteCitationValidator(p).validate(report, evidence);
            assertEquals(VerificationState.COMPLETED, report.getVerificationState());
            assertEquals(1, report.getSupportedClaims());
            assertEquals(1, report.getInsufficientClaims());
            assertEquals(2, report.getClaims().get(0).getStartIndex());
            assertEquals(name.equals("batch") ? 1 : 3, report.getLlmCalls());
            assertTrue(retriever.retrieve("公司食堂周末几点开门？", 3).isEmpty());
        } finally { index.close(); }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
