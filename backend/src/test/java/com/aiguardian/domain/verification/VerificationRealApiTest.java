package com.aiguardian.domain.verification;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.*;
import com.aiguardian.infrastructure.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 显式启用的手工实验入口；普通 mvn test 不连接真实 API。 */
@EnabledIfSystemProperty(named = "runRealVerification", matches = "true")
class VerificationRealApiTest {
    @Test void recordTenSamples() throws Exception {
        var json = new ObjectMapper();
        var config = new AiGuardianProperties();
        var values = new HashMap<String, String>();
        Path env = Path.of("../.env");
        if (Files.exists(env)) for (String line : Files.readAllLines(env)) {
            line = line.strip();
            if (line.startsWith("#") || !line.contains("=")) continue;
            int split = line.indexOf('=');
            String value = line.substring(split + 1).strip();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
            values.put(line.substring(0, split).strip(), value);
        }
        for (String key : List.of("LLM_BASE_URL", "LLM_MODEL", "LLM_API_KEY")) {
            if (System.getenv(key) != null) values.put(key, System.getenv(key));
            assertTrue(values.containsKey(key) && !values.get(key).isBlank(), "缺少配置：" + key);
        }
        config.getLlm().setBaseUrl(values.get("LLM_BASE_URL"));
        config.getLlm().setModel(values.get("LLM_MODEL"));
        config.getLlm().setApiKey(values.get("LLM_API_KEY"));
        var real = new RealLLMClient(json, config);
        var fixtures = json.readTree(Path.of("../docs/verification/samples.json").toFile());
        var results = new ArrayList<Object>();
        var summary = new LinkedHashMap<String, Object>();
        summary.put("startedAt", OffsetDateTime.now().toString());
        summary.put("model", values.get("LLM_MODEL"));
        summary.put("promptVersion", "v1");
        summary.put("execution", "真实 API；程序逐条执行，人工语义复核另行确认");
        summary.put("results", results);
        Path output = Path.of("../docs/verification/real-api-results.json");
        for (var fixture : fixtures) {
            var input = VerificationInput.builder().requestId("req_manual_" + fixture.get("id").asText())
                    .mode(RunMode.REAL).question(fixture.get("question").asText())
                    .answer(fixture.get("answer").asText()).evidence(List.of(json.treeToValue(fixture.get("evidence"), Evidence.class))).build();
            var raw = new ArrayList<String>();
            var verifier = new LLMBatchClaimVerifier(request -> {
                String content = real.generate(request);
                raw.add(content);
                return content;
            }, json, config);
            long started = System.nanoTime();
            var report = verifier.verify(input);
            new ExactQuoteCitationValidator(config).validate(report, input.getEvidence());
            var item = new LinkedHashMap<String, Object>();
            item.put("sampleId", fixture.get("id").asText());
            item.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            item.put("expected", fixture.get("expected"));
            item.put("rawResponses", raw);
            item.put("report", report);
            results.add(item);
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), summary);
        }
        summary.put("finishedAt", OffsetDateTime.now().toString());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), summary);
        assertEquals(10, results.size());
    }
}
