package com.aiguardian.common;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {}

    public static String requestId() {
        return "req_" + randomHex();
    }

    public static String sessionId() {
        return "ses_" + randomHex();
    }

    /** P1 工具调用编号（契约 3.11）。 */
    public static String toolCallId() {
        return "tool_" + randomHex();
    }

    private static String randomHex() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
