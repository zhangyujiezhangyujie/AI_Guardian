package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** One bounded real batch pass over the confirmed 10 answers / 30 labels. No Spring/database. */
@EnabledIfSystemProperty(named="runPhase4RealEvaluation", matches="true")
class Phase4RealEvaluationTest {
    final ObjectMapper json = new ObjectMapper();

    @Test void evaluateConfirmedDataset() throws Exception {
        Path datasetPath=Path.of("../docs/verification/annotations-reviewed.json");
        var dataset=json.readTree(datasetPath.toFile());
        assertEquals("REVIEWED",dataset.path("status").asText());
        assertTrue(dataset.path("humanReviewed").asBoolean());
        assertEquals(30, java.util.stream.StreamSupport.stream(dataset.get("cases").spliterator(),false)
                .mapToInt(c->c.get("claims").size()).sum());
        var config=configuration();
        String runId=OffsetDateTime.now(ZoneOffset.ofHours(8)).toString().replaceAll("[^0-9]", "");
        Path out=Path.of("../docs/verification/phase4/runs",runId);
        Files.createDirectories(out);
        Files.copy(datasetPath,out.resolve("dataset.json"));
        var root=new LinkedHashMap<String,Object>();
        root.put("startedAt",OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        root.put("kind","REAL_MODEL_FIXED_EVIDENCE_MODULE_EVALUATION");
        root.put("plannedExpectedClaims",30);
        root.put("plannedCases",10);
        root.put("model",config.getLlm().getModel());
        var uri=java.net.URI.create(config.getLlm().getBaseUrl());
        root.put("modelServiceHost",uri.getHost());
        root.put("configuration",Map.of("temperature",0.0,"jsonMode",true,"verifyTimeoutMs",config.getLlm().getVerifyTimeoutMs(),
                "maxAttempts",config.getLlm().getVerifyMaxAttempts(),"maxClaims",8,"minQuoteChars",6,"verifier","batch"));
        root.put("datasetSha256",hash(datasetPath));
        root.put("sourceHashes",sourceHashes());
        root.put("gitHead",git("rev-parse","HEAD"));
        root.put("mergeHead",git("rev-parse","-q","--verify","MERGE_HEAD"));
        root.put("workspaceStatus",git("status","--short"));
        root.put("javaVersion",System.getProperty("java.version"));
        var rows=new ArrayList<Object>(); root.put("cases",rows);
        var scores=new ArrayList<Phase4Metrics.CaseScore>();
        var reports=new ArrayList<VerificationReport>();
        save(out,root);
        var real=new RealLLMClient(json,config);
        for(var fixture:dataset.get("cases")) {
            var evidence=new ArrayList<Evidence>();
            for(var id:fixture.get("evidenceIds")) for(var source:dataset.get("sources")) {
                if(id.asText().equals(source.path("evidenceId").asText())) evidence.add(json.treeToValue(source,Evidence.class));
            }
            var input=VerificationInput.builder().requestId("req_eval_"+fixture.get("caseId").asText())
                    .mode(RunMode.REAL).question(fixture.get("question").asText())
                    .answer(fixture.get("answer").asText()).evidence(evidence).build();
            var row=new LinkedHashMap<String,Object>();
            row.put("caseId",fixture.get("caseId").asText()); row.put("input",input);
            row.put("expected",fixture.get("claims"));
            var attempts=new ArrayList<Object>(); row.put("attempts",attempts); rows.add(row);
            var verifier=new LLMBatchClaimVerifier(request->{
                var attempt=new LinkedHashMap<String,Object>(); attempts.add(attempt);
                attempt.put("request",request); // prompts contain only unlabelled input and evidence
                long start=System.nanoTime();
                try { String raw=real.generate(request); attempt.put("rawContent",raw); return raw; }
                catch(LLMException e){ attempt.put("failureType",e.getType().name()); throw e; }
                finally {
                    attempt.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0);
                    try { save(out,root); } catch(Exception e){throw new IllegalStateException("Cannot persist experiment record",e);}
                }
            },json,config);
            long start=System.nanoTime();
            var report=verifier.verify(input);
            new ExactQuoteCitationValidator(config).validate(report,evidence);
            row.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0);
            row.put("report",report); row.put("observedCalls",attempts.size());
            assertEquals(attempts.size(),report.getLlmCalls());
            var score=Phase4Metrics.align(fixture,report); row.put("alignment",score);
            scores.add(score); reports.add(report);
            root.put("summary",Phase4Metrics.summarize(scores,reports)); save(out,root);
        }
        root.put("finishedAt",OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        root.put("completedCases",rows.size()); save(out,root);
        Files.writeString(out.getParent().getParent().resolve("latest-run.txt"),out.getFileName().toString()+"\n");
    }

    static AiGuardianProperties configuration() throws Exception {
        var values=new HashMap<String,String>();
        Path env=Path.of("../.env");
        if(Files.exists(env)) for(String line:Files.readAllLines(env)) {
            line=line.strip(); if(line.startsWith("#")||!line.contains("="))continue;
            int i=line.indexOf('='); String v=line.substring(i+1).strip();
            if(v.length()>=2&&((v.startsWith("\"")&&v.endsWith("\""))||(v.startsWith("'")&&v.endsWith("'"))))v=v.substring(1,v.length()-1);
            values.put(line.substring(0,i).strip(),v);
        }
        for(String key:List.of("LLM_BASE_URL","LLM_MODEL","LLM_API_KEY")) {
            if(System.getenv(key)!=null) values.put(key,System.getenv(key));
            if(values.getOrDefault(key,"").isBlank())throw new IllegalStateException("Missing "+key);
        }
        var c=new AiGuardianProperties();
        c.getLlm().setBaseUrl(values.get("LLM_BASE_URL")); c.getLlm().setModel(values.get("LLM_MODEL"));
        c.getLlm().setApiKey(values.get("LLM_API_KEY")); return c;
    }
    void save(Path out,Object root) throws Exception { json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("evaluation.json").toFile(),root); }
    static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    static Map<String,String> sourceHashes() throws Exception {
        var hashes=new TreeMap<String,String>();
        for(String dir:List.of("src/main","src/test","../knowledge-base"))try(var paths=Files.walk(Path.of(dir))){
            for(var path:paths.filter(Files::isRegularFile).toList())hashes.put(path.toString().replace('\\','/'),hash(path));
        }
        hashes.put("pom.xml",hash(Path.of("pom.xml"))); return hashes;
    }
    static String git(String...args) throws Exception {
        var command=new ArrayList<>(List.of("git","-c","safe.directory="+Path.of("..").toAbsolutePath().normalize(),"-C",".."));
        command.addAll(List.of(args)); var p=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); p.waitFor(); return output.strip();
    }
}
