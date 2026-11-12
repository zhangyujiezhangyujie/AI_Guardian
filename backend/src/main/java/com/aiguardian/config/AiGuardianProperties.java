package com.aiguardian.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "aiguardian")
public class AiGuardianProperties {
    private final Llm llm = new Llm();
    private final Retrieval retrieval = new Retrieval();
    private final Verification verification = new Verification();
    private final OutputGuard outputGuard = new OutputGuard();
    private final Demo demo = new Demo();

    @Getter
    @Setter
    public static class Llm {
        private String baseUrl;
        private String apiKey;
        private String model;
        private long generateTimeoutMs = 30000;
        private long verifyTimeoutMs = 45000;
        private int verifyMaxAttempts = 2;
    }

    @Getter
    @Setter
    public static class Retrieval {
        private String knowledgeBaseDir = "../knowledge-base";
        private int topK = 3;
        private double minScore = 0;
        private int chunkMinChars = 200;
        private int chunkMaxChars = 400;
        private int chunkOverlapChars = 40;
    }

    @Getter
    @Setter
    public static class Verification {
        private String verifier = "batch";
        private int maxClaims = 8;
        private int minQuoteChars = 6;
    }

    @Getter
    @Setter
    public static class OutputGuard {
        private String strategy = "annotate";
        private double blockThreshold = 0.5;
        private boolean strictForToolCall = true;
    }

    @Getter
    @Setter
    public static class Demo {
        private String testCasesDir = "../test-cases";
    }
}
