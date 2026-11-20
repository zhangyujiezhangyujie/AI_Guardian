package com.aiguardian.domain.verification;

import com.aiguardian.application.*;
import com.aiguardian.audit.*;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Full business chain, in-process entry; real retrieval/model/JDBC, no test replacements. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named="runPhase4Performance",matches="smoke|formal")
class Phase4PerformanceTest {
    @Autowired ChatOrchestrator orchestrator;
    @Autowired AuditQueryService audit;
    @Autowired AiGuardianProperties properties;
    @Autowired ApplicationContext context;
    @Autowired ObjectMapper json;
    @SpyBean RealLLMClient real; // observe and call the real method; never stub its response
    final List<Map<String,Object>> calls=new ArrayList<>();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        // Fail closed before creating the application context or making any real model calls.
        Path gate=Path.of("../docs/experiments/performance/regression-gate.json");
        assertTrue(Files.exists(gate),"Run the offline regression wrapper successfully first");
        var receipt=new ObjectMapper().readTree(gate.toFile());
        assertTrue(receipt.path("allPassed").asBoolean());
        assertEquals(new ObjectMapper().valueToTree(Phase4RealEvaluationTest.sourceHashes()),receipt.get("sourceHashes"));
        for(var file:receipt.path("reports")) {
            Path p=Path.of(file.path("path").asText());
            assertEquals(file.path("sha256").asText(),Phase4RealEvaluationTest.hash(p));
        }
        assertTrue(receipt.path("retrievalIntegrationPassed").asBoolean());
        var llm=Phase4RealEvaluationTest.configuration().getLlm();
        r.add("aiguardian.llm.base-url",llm::getBaseUrl);
        r.add("aiguardian.llm.model",llm::getModel);
        r.add("aiguardian.llm.api-key",llm::getApiKey);
        // DB_USERNAME/DB_PASSWORD must be exported to this process. No in-memory DB or stub profile.
    }

    @Test void measure() throws Exception {
        assertFalse(Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("stub"));
        assertTrue(context.getBean(EvidenceRetriever.class).getClass().getName().contains("Lucene"));
        assertInstanceOf(JdbcAuditService.class,context.getBean(AuditService.class));
        assertInstanceOf(JdbcAuditQueryService.class,audit);
        assertInstanceOf(ExactQuoteCitationValidator.class,context.getBean(CitationValidator.class));
        assertNotNull(context.getBean(LLMBatchClaimVerifier.class));
        assertNotNull(context.getBean(PerClaimClaimVerifier.class));
        assertTrue(context.getBean(OutputGuardRegistry.class).availableStrategies().containsAll(List.of("annotate","block")));
        String kind=System.getProperty("runPhase4Performance");
        Path out=Path.of("../docs/experiments/performance",kind+"-"+System.currentTimeMillis());
        Files.createDirectories(out);
        var root=new LinkedHashMap<String,Object>(); var rows=new ArrayList<Map<String,Object>>();
        root.put("kind",kind); root.put("records",rows); root.put("startedAt",OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        root.put("head",Phase4RealEvaluationTest.git("rev-parse","HEAD"));
        root.put("mergeHead",Phase4RealEvaluationTest.git("rev-parse","-q","--verify","MERGE_HEAD"));
        root.put("workspace",Phase4RealEvaluationTest.git("status","--short"));
        root.put("sourceHashes",Phase4RealEvaluationTest.sourceHashes());
        root.put("model",properties.getLlm().getModel());
        root.put("modelServiceHost",java.net.URI.create(properties.getLlm().getBaseUrl()).getHost());
        root.put("configuration",Map.of("verification",properties.getVerification(),"retrieval",properties.getRetrieval(),
                "guard",properties.getOutputGuard(),"generateTemperature",0.3,"verifyTemperature",0.0,
                "generateTimeoutMs",properties.getLlm().getGenerateTimeoutMs(),"verifyTimeoutMs",properties.getLlm().getVerifyTimeoutMs(),
                "maxAttempts",properties.getLlm().getVerifyMaxAttempts()));
        doAnswer(invocation->{
            LLMRequest request=invocation.getArgument(0); assertEquals(RunMode.REAL,request.getMode());
            var call=new LinkedHashMap<String,Object>(); calls.add(call); call.put("request",request);
            long start=System.nanoTime();
            try { Object value=invocation.callRealMethod(); call.put("rawContent",value); return value; }
            catch(LLMException e){call.put("failureType",e.getType().name());throw e;}
            finally{call.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0);}
        }).when(real).generate(any());
        // Three bounded REAL probes also constitute one warmup for each arm. Every probe must
        // demonstrate retrieval, actual generation, verification/citations (B/C) and JDBC readback.
        String[] arms={"none","per-claim","batch"};
        for(String arm:arms) {
            var row=one(arm,"公司正式员工一年有多少天年假？","warmup",0);
            rows.add(row); save(out,root); assertEquals(true,row.get("success"),"Real warmup/audit gate failed");
        }
        root.put("environmentGatePassed",true); save(out,root);
        if(kind.equals("formal")) for(int round=0;round<20;round++) {
            String question=round%2==0?"公司正式员工一年有多少天年假？":"公司员工有哪些福利？";
            for(int offset=0;offset<3;offset++) {
                String arm=arms[(round+offset)%3];
                rows.add(one(arm,question,"formal",round+1)); save(out,root);
            }
        }
        root.put("summary",summary(rows)); root.put("finishedAt",OffsetDateTime.now(ZoneOffset.ofHours(8)).toString()); save(out,root);
    }
    Map<String,Object> one(String arm,String question,String phase,int round) {
        calls.clear(); var row=new LinkedHashMap<String,Object>();
        row.put("phase",phase);row.put("round",round);row.put("arm",arm);row.put("question",question);
        ChatResponse response=null; long start=System.nanoTime();
        try {
            response=orchestrator.handle(ChatCommand.builder().question(question).mode(RunMode.REAL)
                    .verifierOverride(arm).guardStrategyOverride("annotate").blockThresholdOverride(0.5).build());
        } catch(Exception e){row.put("exceptionType",e.getClass().getSimpleName());}
        // Primary clock includes synchronous audit persistence, excludes post-request audit readback.
        row.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0);
        row.put("calls",new ArrayList<>(calls));row.put("llmCalls",calls.size());row.put("response",response);
        boolean success=false;
        if(response!=null)try {
            var persisted=audit.findByRequestId(response.getRequestId());row.put("auditReadback",persisted);
            var report=response.getVerification();
            long verifyCalls=calls.stream().filter(c->((LLMRequest)c.get("request")).getPurpose()==LLMPurpose.VERIFY_CLAIMS).count();
            success=!response.getEvidence().isEmpty() && !calls.isEmpty() && persisted.mode()==RunMode.REAL
                    && persisted.requestId().equals(response.getRequestId()) && persisted.rawAnswer()!=null
                    && persisted.events().stream().anyMatch(e->e.getEventType()==AuditEventType.REQUEST_COMPLETED)
                    && report.getVerifierName().equals(arm) && report.getLlmCalls()==verifyCalls
                    && response.getGuard()!=null;
            if(arm.equals("none")) success=success && verifyCalls==0 && response.getGuard().getAction()==GuardAction.NO_CLAIMS;
            else success=success && report.getVerificationState()==VerificationState.COMPLETED
                    && response.getGuard().getAction()==GuardAction.ANNOTATED
                    && !report.getClaims().isEmpty()
                    && persisted.events().stream().anyMatch(e->e.getEventType()==AuditEventType.CITATION_CHECKED)
                    && report.getClaims().stream().anyMatch(c->Boolean.TRUE.equals(c.getQuoteExists()));
        }catch(Exception e){row.put("auditFailureType",e.getClass().getSimpleName());}
        row.put("success",success);
        row.put("timeout",calls.stream().anyMatch(c->"TIMEOUT".equals(c.get("failureType")))); return row;
    }
    static Map<String,Object> summary(List<Map<String,Object>> rows) {
        var result=new LinkedHashMap<String,Object>();
        for(String arm:List.of("none","per-claim","batch")) {
            var selected=rows.stream().filter(r->r.get("phase").equals("formal")&&r.get("arm").equals(arm)).toList();
            var times=selected.stream().mapToDouble(r->(double)r.get("elapsedMs")).sorted().toArray();
            var good=selected.stream().filter(r->Boolean.TRUE.equals(r.get("success"))).toList();
            var item=new LinkedHashMap<String,Object>();item.put("attempts",selected.size());item.put("successes",good.size());
            item.put("failures",selected.size()-good.size());item.put("timeouts",selected.stream().filter(r->Boolean.TRUE.equals(r.get("timeout"))).count());
            item.put("meanMsAllAttempts",Arrays.stream(times).average().isPresent()?Arrays.stream(times).average().getAsDouble():null);
            item.put("p50MsAllAttempts",percentile(times,.5));item.put("p95MsAllAttempts",percentile(times,.95));
            item.put("llmCallsTotal",selected.stream().mapToInt(r->(int)r.get("llmCalls")).sum());
            item.put("meanMsSuccessOnly",good.stream().mapToDouble(r->(double)r.get("elapsedMs")).average().isPresent()
                    ?good.stream().mapToDouble(r->(double)r.get("elapsedMs")).average().getAsDouble():null);
            result.put(arm,item);
        }return result;
    }
    static Double percentile(double[] sorted,double p){return sorted.length==0?null:sorted[(int)Math.ceil(p*sorted.length)-1];}
    void save(Path out,Object data)throws Exception{json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("performance.json").toFile(),data);}
}
