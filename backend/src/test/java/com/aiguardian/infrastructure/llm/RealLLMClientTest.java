package com.aiguardian.infrastructure.llm;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.LLMPurpose;
import com.aiguardian.domain.model.LLMRequest;
import com.aiguardian.domain.model.RunMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RealLLMClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private RealLLMClient client;

    @BeforeEach
    void startLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        var properties = new AiGuardianProperties();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setModel("test-model");
        client = new RealLLMClient(json, properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsChatCompletionsWithOptionalJsonMode() throws Exception {
        var postedBody = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                postedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"15天\"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });

        assertThat(client.generate(request(false, 3000))).isEqualTo("15天");
        assertThat(auth.get()).isEqualTo("Bearer test-key");
        var plain = json.readTree(postedBody.get());
        assertThat(plain.path("model").asText()).isEqualTo("test-model");
        assertThat(plain.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(plain.path("messages").path(1).path("content").asText()).isEqualTo("年假？");
        assertThat(plain.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(plain.has("response_format")).isFalse();

        client.generate(request(true, 3000));
        assertThat(json.readTree(postedBody.get()).at("/response_format/type").asText()).isEqualTo("json_object");
    }

    @Test
    void distinguishesHttpErrorsAndEmptyResponses() {
        var code = new AtomicReference<>(429);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code.get(), body.length);
                exchange.getResponseBody().write(body);
            }
        });

        assertThat(failure(3000).getType()).isEqualTo(LLMException.Type.API_ERROR);
        code.set(200);
        assertThat(failure(3000).getType()).isEqualTo(LLMException.Type.EMPTY_RESPONSE);
    }

    @Test
    void mapsRequestTimeout() {
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        assertThat(failure(50).getType()).isEqualTo(LLMException.Type.TIMEOUT);
    }

    private LLMException failure(long timeout) {
        return catchThrowableOfType(() -> client.generate(request(false, timeout)), LLMException.class);
    }

    private LLMRequest request(boolean jsonMode, long timeout) {
        return LLMRequest.builder().requestId("req_000000000001").mode(RunMode.REAL)
                .purpose(LLMPurpose.GENERATE_ANSWER).attempt(1).temperature(0.3)
                .systemPrompt("返回所需格式").userPrompt("年假？").jsonMode(jsonMode).timeoutMs(timeout).build();
    }
}
