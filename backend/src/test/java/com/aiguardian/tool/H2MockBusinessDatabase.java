package com.aiguardian.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 测试用 H2（MySQL 兼容模式）数据库，执行仓库中真实的 db/init.sql（P1 部分）与 db/reset_mock_business_record.sql，
 * 同时验证这两个脚本本身可执行。
 */
public final class H2MockBusinessDatabase {

    private static final String P1_MARKER = "-- P1 工具调用安全网关";

    private H2MockBusinessDatabase() {
    }

    public static JdbcTemplate create() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tool_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        execute(jdbc, p1Section(read("db/init.sql")));
        return jdbc;
    }

    /** 执行 db/reset_mock_business_record.sql（跳过只适用于 MySQL 的 USE 语句）。 */
    public static void reset(JdbcTemplate jdbc) {
        execute(jdbc, read("db/reset_mock_business_record.sql"));
    }

    private static String p1Section(String initSql) {
        int start = initSql.indexOf(P1_MARKER);
        if (start < 0) {
            throw new IllegalStateException("db/init.sql 缺少 P1 段落");
        }
        return initSql.substring(start);
    }

    static void execute(JdbcTemplate jdbc, String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.toUpperCase().startsWith("USE ")) {
                continue;
            }
            withoutComments.append(line).append('\n');
        }
        for (String statement : withoutComments.toString().split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
    }

    static String read(String relative) {
        Path working = Path.of("").toAbsolutePath();
        for (Path base : new Path[] {working, working.getParent()}) {
            Path file = base.resolve(relative);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("找不到 " + relative);
    }
}
