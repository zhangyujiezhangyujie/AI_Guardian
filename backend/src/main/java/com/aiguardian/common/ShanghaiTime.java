package com.aiguardian.common;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class ShanghaiTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ShanghaiTime() {}

    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MILLIS);
    }
}
