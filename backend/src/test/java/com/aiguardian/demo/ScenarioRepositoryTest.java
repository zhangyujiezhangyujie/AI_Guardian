package com.aiguardian.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.LLMPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioRepositoryTest {

    private JsonScenarioRepository repository;

    @BeforeEach
    void setUp() {
        repository = repository("../test-cases");
        repository.load();
    }

    @Test
    void loadsAllScenarioFilesWithMatchingIds() {
        List<Scenario> scenarios = repository.findAll();

        assertFalse(scenarios.isEmpty());
        for (Scenario scenario : scenarios) {
            assertNotNull(scenario.getScenarioId());
            assertFalse(scenario.getQuestion().isBlank(), scenario.getScenarioId() + " 缺少 question");
            assertNotNull(scenario.getMock(), scenario.getScenarioId() + " 缺少 mock");
            assertNotNull(scenario.getExpected(), scenario.getScenarioId() + " 缺少 expected");
            assertTrue(scenario.getExpected().getHttpStatus() >= 200);
        }
        List<String> ids = scenarios.stream().map(Scenario::getScenarioId).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "场景应按 scenarioId 升序");
    }

    @Test
    void scenarioIdMatchesFileName() {
        for (Scenario scenario : repository.findAll()) {
            assertTrue(Files.isRegularFile(Path.of("../test-cases", scenario.getScenarioId() + ".json")),
                    scenario.getScenarioId() + " 与文件名不一致");
        }
    }

    @Test
    void findsByIdAndReturnsEmptyForUnknown() {
        assertTrue(repository.findById("SC01").isPresent());
        assertTrue(repository.findById("SC02").isPresent());
        assertTrue(repository.findById("SC99").isEmpty());
        assertTrue(repository.findById(null).isEmpty());
    }

    @Test
    void mockKeysArePurposeNames() {
        Scenario scenario = repository.findById("SC01").orElseThrow();
        assertTrue(scenario.getMock().containsKey(LLMPurpose.GENERATE_ANSWER));
        assertTrue(scenario.getMock().containsKey(LLMPurpose.VERIFY_CLAIMS));
    }

    @Test
    void failsWhenScenarioIdDoesNotMatchFileName(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("SC01.json"), """
                {"scenarioId":"SC99","question":"q","mock":{},"expected":{"httpStatus":200}}
                """);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository(directory.toString()).load());
        assertTrue(error.getMessage().contains("场景编号与文件名不一致"));
    }

    @Test
    void failsWhenJsonIsMalformed(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("SC01.json"), "{ not json ");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository(directory.toString()).load());
        assertTrue(error.getMessage().contains("场景文件格式错误"));
    }

    private static JsonScenarioRepository repository(String directory) {
        AiGuardianProperties properties = new AiGuardianProperties();
        properties.getDemo().setTestCasesDir(directory);
        return new JsonScenarioRepository(properties, new ObjectMapper());
    }
}
