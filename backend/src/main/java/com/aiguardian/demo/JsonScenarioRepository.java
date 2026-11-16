package com.aiguardian.demo;

import com.aiguardian.config.AiGuardianProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 从 {@code aiguardian.demo.test-cases-dir} 加载全部场景 JSON（契约 10.1）。
 *
 * <p>格式错误时启动失败并打印出错文件名；目录不存在时只记录警告，视为没有场景
 * （REAL 模式不需要场景，MOCK 模式会在调用时报“场景不存在”）。
 */
@Slf4j
@Component
@Profile("!stub")
public class JsonScenarioRepository implements ScenarioRepository {

    private final AiGuardianProperties properties;
    private final ObjectMapper json;
    private final Map<String, Scenario> byId = new LinkedHashMap<>();

    public JsonScenarioRepository(AiGuardianProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @PostConstruct
    public void load() {
        Path directory = Path.of(properties.getDemo().getTestCasesDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            log.warn("场景目录不存在，MOCK 模式不可用：{}", directory);
            return;
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("无法读取场景目录：" + directory, e);
        }
        for (Path file : files) {
            Scenario scenario = parse(file);
            validate(scenario, file);
            if (byId.putIfAbsent(scenario.getScenarioId(), scenario) != null) {
                throw new IllegalStateException("场景编号重复：" + scenario.getScenarioId() + "，文件：" + file.getFileName());
            }
        }
        log.info("已加载 {} 个测试场景：{}", byId.size(), byId.keySet());
    }

    @Override
    public List<Scenario> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Scenario> findById(String scenarioId) {
        return scenarioId == null ? Optional.empty() : Optional.ofNullable(byId.get(scenarioId));
    }

    private Scenario parse(Path file) {
        try {
            return json.readValue(Files.readString(file, StandardCharsets.UTF_8), Scenario.class);
        } catch (IOException e) {
            throw new IllegalStateException("场景文件格式错误：" + file.getFileName(), e);
        }
    }

    private void validate(Scenario scenario, Path file) {
        String fileName = file.getFileName().toString();
        if (scenario == null) {
            throw new IllegalStateException("场景文件为空：" + fileName);
        }
        String expectedId = fileName.substring(0, fileName.length() - ".json".length());
        if (scenario.getScenarioId() == null || !scenario.getScenarioId().equals(expectedId)) {
            throw new IllegalStateException("场景编号与文件名不一致：" + fileName);
        }
        if (scenario.getQuestion() == null || scenario.getQuestion().isBlank()) {
            throw new IllegalStateException("场景缺少 question：" + fileName);
        }
        if (scenario.getMock() == null) {
            throw new IllegalStateException("场景缺少 mock：" + fileName);
        }
        if (scenario.getExpected() == null || scenario.getExpected().getHttpStatus() <= 0) {
            throw new IllegalStateException("场景缺少 expected.httpStatus：" + fileName);
        }
    }
}
