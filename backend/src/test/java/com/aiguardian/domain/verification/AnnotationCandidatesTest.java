package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 数据完整性与模块回放测试，不把候选标签的回放当作模型准确率。 */
class AnnotationCandidatesTest {
    final ObjectMapper json = new ObjectMapper();
    JsonNode dataset(String file) throws Exception {
        return json.readTree(Path.of("../docs/verification/" + file).toFile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotation-candidates.json", "annotations-reviewed.json"})
    void thirtyClaimsHaveEvidenceRationalesAndHonestReviewStatus(String file) throws Exception {
        var data = dataset(file);
        var ids = new HashSet<String>();
        var sourceIds = new HashSet<String>();
        for (var source : data.get("sources")) assertTrue(sourceIds.add(source.get("evidenceId").asText()));
        boolean allReviewed = true;
        int count = 0;
        for (var item : data.get("cases")) {
            for (var id : item.get("evidenceIds")) assertTrue(sourceIds.contains(id.asText()));
            for (var claim : item.get("claims")) {
                count++;
                assertTrue(ids.add(claim.get("id").asText()));
                assertTrue(item.get("answer").asText().contains(claim.get("originalText").asText()));
                assertFalse(claim.get("rationale").asText().isBlank());
                assertTrue(Set.of("SUPPORTED", "INSUFFICIENT").contains(claim.get("proposedStatus").asText()));
                if (claim.get("reviewStatus").asText().equals("REVIEWED")) {
                    assertFalse(claim.path("annotator").asText("").isBlank());
                    assertFalse(claim.path("reviewer").asText("").isBlank());
                } else if (claim.get("reviewStatus").asText().equals("AI_REVIEWED")) {
                    assertEquals("AI_REVIEWED", data.get("status").asText());
                    assertFalse(claim.get("humanReviewed").asBoolean());
                    assertTrue(claim.get("reviewer").asText().contains("AI"));
                    allReviewed = false;
                } else {
                    allReviewed = false;
                    assertEquals("PENDING_HUMAN_REVIEW", claim.get("reviewStatus").asText());
                }
            }
        }
        assertEquals(30, count);
        if (data.get("status").asText().equals("REVIEWED")) assertTrue(allReviewed);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "annotation-candidates.json,batch", "annotation-candidates.json,per-claim",
        "annotations-reviewed.json,batch", "annotations-reviewed.json,per-claim"})
    void replayCandidateResponsesThroughBothImplementations(String file, String name) throws Exception {
        var data = dataset(file);
        var config = new AiGuardianProperties();
        for (var item : data.get("cases")) {
            var sources = new ArrayList<Evidence>();
            for (var source : data.get("sources")) {
                for (var id : item.get("evidenceIds")) {
                    if (id.asText().equals(source.get("evidenceId").asText())) {
                        sources.add(json.treeToValue(source, Evidence.class));
                    }
                }
            }
            com.aiguardian.infrastructure.llm.LLMClient model = request -> {
                try {
                    var input = json.readTree(request.getUserPrompt());
                    var root = json.createObjectNode();
                    root.put("truncated", false);
                    var output = root.putArray("claims");
                    for (var claim : item.get("claims")) {
                        if (input.has("target") && !input.get("target").get("claim").asText()
                                .equals(claim.get("claim").asText())) continue;
                        var node = output.addObject();
                        node.put("claim", claim.get("claim").asText());
                        node.put("originalText", claim.get("originalText").asText());
                        node.put("supported", claim.get("proposedStatus").asText().equals("SUPPORTED"));
                        node.set("evidenceId", claim.get("referenceEvidenceId"));
                        node.set("quote", claim.get("referenceQuote"));
                    }
                    return json.writeValueAsString(root);
                } catch (Exception e) { throw new IllegalStateException(e); }
            };
            ClaimVerifier verifier = name.equals("batch") ? new LLMBatchClaimVerifier(model, json, config)
                    : new PerClaimClaimVerifier(model, json, config);
            var input = VerificationInput.builder().requestId(item.get("caseId").asText()).mode(RunMode.REAL)
                    .question(item.get("question").asText()).answer(item.get("answer").asText()).evidence(sources).build();
            var report = verifier.verify(input);
            new ExactQuoteCitationValidator(config).validate(report, sources);
            assertEquals(VerificationState.COMPLETED, report.getVerificationState());
            assertEquals(3, report.getTotalClaims());
            assertEquals(name.equals("batch") ? 1 : 4, report.getLlmCalls());
            for (int i = 0; i < report.getClaims().size(); i++) {
                var actual = report.getClaims().get(i);
                assertEquals(item.get("claims").get(i).get("proposedStatus").asText(), actual.getStatus().name());
                assertNotNull(actual.getStartIndex());
                assertEquals(actual.getOriginalText(), input.getAnswer()
                        .substring(actual.getStartIndex(), actual.getEndIndex()));
            }
        }
    }

    @Test void reviewedEvidenceMatchesKnowledgeFilesAndEveryCitationIsVerbatim() throws Exception {
        var data = dataset("annotations-reviewed.json");
        assertTrue(data.get("humanReviewed").asBoolean());
        assertEquals("REVIEWED", data.get("status").asText());
        assertEquals("成员3（用户）", data.path("humanReviewConfirmation").path("reportedBy").asText());
        assertEquals(2, data.path("humanReviewConfirmation").path("reviewers").size());
        for (var item : data.get("cases")) for (var claim : item.get("claims")) {
            assertTrue(claim.path("humanReviewed").asBoolean());
            assertEquals("REVIEWED", claim.path("reviewStatus").asText());
            assertEquals("Codex (AI)", claim.path("annotator").asText());
            assertFalse(claim.path("aiReviewer").asText().isBlank());
        }
        assertEquals("OFFICIAL_CHUNKER_IDS", data.get("evidenceIdStatus").asText());
        var byId = new HashMap<String, JsonNode>();
        for (var s : data.get("sources")) byId.put(s.get("evidenceId").asText(), s);
        for (var source : data.get("sourceManifest")) {
            var path = Path.of("..", source.get("path").asText());
            assertEquals(source.get("sha256").asText(), sha256(java.nio.file.Files.readAllBytes(path)));
        }
        for (var mapping : data.get("evidenceManifest")) {
            String text = java.nio.file.Files.readString(Path.of("..", mapping.get("path").asText()))
                    .replace("\r\n", "\n").replace('\r', '\n');
            String body = text.substring(text.indexOf('\n') + 1);
            String assembled = body.substring(mapping.get("bodyStartUtf16").asInt(), mapping.get("bodyEndUtf16").asInt());
            assertEquals(assembled, byId.get(mapping.get("evidenceId").asText()).get("content").asText());
            assertEquals(mapping.get("contentSha256").asText(), sha256(assembled.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        int supported = 0;
        for (var item : data.get("cases")) for (var claim : item.get("claims")) {
            assertTrue(byId.get(claim.get("reviewEvidenceId").asText()).get("content").asText()
                    .contains(claim.get("reviewQuote").asText()));
            if (claim.get("proposedStatus").asText().equals("SUPPORTED")) {
                supported++;
                String id = claim.get("referenceEvidenceId").asText();
                assertTrue(byId.get(id).get("content").asText().contains(claim.get("referenceQuote").asText()));
                assertTrue(item.get("evidenceIds").toString().contains("\"" + id + "\""));
            } else {
                assertTrue(claim.get("referenceEvidenceId").isNull());
                assertTrue(claim.get("referenceQuote").isNull());
            }
        }
        assertEquals(15, supported);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
