package com.aiguardian.infrastructure.llm;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.LLMRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!stub")
public class RealLLMClient implements LLMClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final AiGuardianProperties.Llm properties;

    public RealLLMClient(ObjectMapper json, AiGuardianProperties properties) {
        this.json = json;
        this.properties = properties.getLlm();
    }

    @Override
    public String generate(LLMRequest request) {
        long started = System.nanoTime();
        Integer status = null;
        try {
            HttpResponse<String> response = http.send(buildRequest(request), HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new LLMException(LLMException.Type.API_ERROR, "模型服务返回 HTTP " + status);
            }
            return readContent(response.body());
        } catch (HttpTimeoutException e) {
            throw new LLMException(LLMException.Type.TIMEOUT, "模型服务调用超时。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException(LLMException.Type.API_ERROR, "模型服务调用中断。");
        } catch (IOException | IllegalArgumentException e) {
            throw new LLMException(LLMException.Type.API_ERROR, "模型请求或响应格式无效，或连接失败。");
        } finally {
            log.info("requestId={} purpose={} durationMs={} status={}", request.getRequestId(),
                    request.getPurpose(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), status);
        }
    }

    private HttpRequest buildRequest(LLMRequest request) throws IOException {
        String baseUrl = properties.getBaseUrl();
        String apiKey = properties.getApiKey();
        String model = properties.getModel();
        if (missing(baseUrl) || missing(apiKey) || missing(model)) {
            throw new LLMException(LLMException.Type.API_ERROR, "模型服务环境变量尚未配置。");
        }
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());
        body.put("temperature", request.getTemperature());
        if (request.isJsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        return HttpRequest.newBuilder(URI.create(baseUrl.strip().replaceFirst("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofMillis(request.getTimeoutMs()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }

    private String readContent(String body) throws IOException {
        JsonNode root = json.readTree(body);
        if (root != null) {
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
        }
        throw new LLMException(LLMException.Type.EMPTY_RESPONSE, "模型服务返回空文本。");
    }

    private static boolean missing(String value) {
        return value == null || value.isBlank() || value.startsWith("${");
    }
}
