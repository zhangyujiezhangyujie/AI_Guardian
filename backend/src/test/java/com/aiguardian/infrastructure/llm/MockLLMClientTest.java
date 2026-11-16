package com.aiguardian.infrastructure.llm;

import static com.aiguardian.domain.model.LLMPurpose.GENERATE_ANSWER;
import static com.aiguardian.domain.model.LLMPurpose.VERIFY_CLAIMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiguardian.demo.MockResponse;
import com.aiguardian.demo.Scenario;
import com.aiguardian.demo.ScenarioExpected;
import com.aiguardian.demo.ScenarioRepository;
import com.aiguardian.domain.model.LLMRequest;
import com.aiguardian.domain.model.LLMPurpose;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MockLLMClientTest {

    @Test
    void picksResponseByAttemptAndClampsToLast() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(
                text("第一次"), text("第二次"))));

        assertEquals("第一次", client.generate(request(GENERATE_ANSWER, 1)));
        assertEquals("第二次", client.generate(request(GENERATE_ANSWER, 2)));
        assertEquals("第二次", client.generate(request(GENERATE_ANSWER, 3)));
    }

    @Test
    void mapsResponseTypesToExceptionTypes() {
        MockLLMClient client = client(Map.of(
                GENERATE_ANSWER, List.of(MockResponse.builder().type("TIMEOUT").build()),
                VERIFY_CLAIMS, List.of(MockResponse.builder().type("API_ERROR").build())));

        assertEquals(LLMException.Type.TIMEOUT,
                assertThrows(LLMException.class, () -> client.generate(request(GENERATE_ANSWER, 1))).getType());
        assertEquals(LLMException.Type.API_ERROR,
                assertThrows(LLMException.class, () -> client.generate(request(VERIFY_CLAIMS, 1))).getType());
    }

    @Test
    void emptyResponseThrowsEmptyResponseType() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(
                MockResponse.builder().type("EMPTY").build())));

        assertEquals(LLMException.Type.EMPTY_RESPONSE,
                assertThrows(LLMException.class, () -> client.generate(request(GENERATE_ANSWER, 1))).getType());
    }

    @Test
    void unconfiguredPurposeThrowsApiError() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(text("只有生成"))));

        LLMException error = assertThrows(LLMException.class,
                () -> client.generate(request(VERIFY_CLAIMS, 1)));
        assertEquals(LLMException.Type.API_ERROR, error.getType());
    }

    @Test
    void unknownTypeThrowsApiError() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(
                MockResponse.builder().type("WAT").build())));

        assertEquals(LLMException.Type.API_ERROR,
                assertThrows(LLMException.class, () -> client.generate(request(GENERATE_ANSWER, 1))).getType());
    }

    @Test
    void missingScenarioThrowsApiError() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(text("x"))));

        LLMException error = assertThrows(LLMException.class,
                () -> client.generate(LLMRequest.builder().scenarioId("SC404").purpose(GENERATE_ANSWER).attempt(1).build()));
        assertEquals(LLMException.Type.API_ERROR, error.getType());
    }

    @Test
    void missingScenarioIdThrowsApiError() {
        MockLLMClient client = client(Map.of(GENERATE_ANSWER, List.of(text("x"))));

        LLMException error = assertThrows(LLMException.class,
                () -> client.generate(LLMRequest.builder().purpose(GENERATE_ANSWER).attempt(1).build()));
        assertEquals(LLMException.Type.API_ERROR, error.getType());
    }

    private static MockLLMClient client(Map<LLMPurpose, List<MockResponse>> mock) {
        Scenario scenario = Scenario.builder().scenarioId("SC99").name("测试").question("测试问题")
                .mock(mock).expected(ScenarioExpected.builder().httpStatus(200).build()).build();
        ScenarioRepository repository = new ScenarioRepository() {
            @Override
            public List<Scenario> findAll() {
                return List.of(scenario);
            }

            @Override
            public Optional<Scenario> findById(String scenarioId) {
                return "SC99".equals(scenarioId) ? Optional.of(scenario) : Optional.empty();
            }
        };
        return new MockLLMClient(repository);
    }

    private static MockResponse text(String content) {
        return MockResponse.builder().type("TEXT").content(content).build();
    }

    private static LLMRequest request(LLMPurpose purpose, int attempt) {
        return LLMRequest.builder().scenarioId("SC99").purpose(purpose).attempt(attempt).build();
    }
}
