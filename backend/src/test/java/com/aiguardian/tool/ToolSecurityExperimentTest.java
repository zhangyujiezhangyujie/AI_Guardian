package com.aiguardian.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.application.ToolCallService;
import com.aiguardian.audit.AuditService;
import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.domain.model.ClaimStatus;
import com.aiguardian.domain.model.ClaimVerification;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolCallResult;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import com.aiguardian.domain.tool.ToolCatalog;
import com.aiguardian.domain.tool.ToolGateway;
import com.aiguardian.infrastructure.persistence.JdbcBusinessRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * P1 安全实验（方案 9.5、任务卡 6.9）：10 条工具调用请求（4 条合法、6 条越权或参数非法），
 * 记录决策、原因，并在每次调用前后用独立 SQL 查询 mock_business_record，证明非 ALLOW 请求不修改数据。
 *
 * <p>默认在 H2（MySQL 兼容模式）上运行，执行仓库中的 db/init.sql 与 reset 脚本。
 * 在真实 MySQL 上复现（库表需已用 db/init.sql 初始化；会先执行 reset 脚本恢复 1001–1005）：
 * <pre>
 * mvn test -Dtest=ToolSecurityExperimentTest \
 *   -DtoolSecurityJdbcUrl="jdbc:mysql://localhost:3306/ai_guardian?serverTimezone=Asia/Shanghai" \
 *   -DtoolSecurityJdbcUser=root -DtoolSecurityJdbcPassword=***
 * </pre>
 * 结果写入 target/tool-security-experiment/（report.md、report.json）。
 */
class ToolSecurityExperimentTest {

    private static final String SUPPORTED_BASIS = "req_000000000001";

    private static final String UNSUPPORTED_BASIS = "req_000000000002";

    /** 一条实验请求及其预期。legitimate=true 表示合法请求（预期放行）。 */
    private record Case(String id, String description, boolean legitimate, String user, String toolName,
                        Map<String, Object> arguments, String basedOnRequestId, String expectedDecision,
                        String expectedReason, Long touchedRecordId) {
    }

    private static List<Case> cases() {
        return List.of(
                new Case("T01", "普通员工检索知识库", true, "user", "search_document",
                        Map.of("keyword", "年假"), null, "ALLOW", null, null),
                new Case("T02", "经理检索知识库", true, "manager", "search_document",
                        Map.of("keyword", "报销材料"), null, "ALLOW", null, null),
                new Case("T03", "经理直接修改记录状态（无依据）", true, "manager", "update_record",
                        Map.of("recordId", 1002, "status", "CLOSED"), null, "ALLOW", null, 1002L),
                new Case("T04", "经理依据全部有证据的回答修改记录", true, "manager", "update_record",
                        Map.of("recordId", 1003, "status", "ARCHIVED"), SUPPORTED_BASIS, "ALLOW", null, 1003L),
                new Case("T05", "普通员工越权删除记录（SC06）", false, "user", "delete_record",
                        Map.of("recordId", 1001), null, "DENY", "PERMISSION_DENIED", null),
                new Case("T06", "普通员工越权修改记录", false, "user", "update_record",
                        Map.of("recordId", 1001, "status", "CLOSED"), null, "DENY", "PERMISSION_DENIED", null),
                new Case("T07", "调用白名单外的工具", false, "manager", "drop_table",
                        Map.of("table", "mock_business_record"), null, "DENY", "TOOL_NOT_ALLOWED", null),
                new Case("T08", "参数非法：编号为字符串、状态不存在", false, "manager", "update_record",
                        Map.of("recordId", "1001", "status", "DELETED"), null, "DENY", "INVALID_ARGUMENTS", null),
                new Case("T09", "依据含证据不足结论的回答修改记录", false, "manager", "update_record",
                        Map.of("recordId", 1004, "status", "OPEN"), UNSUPPORTED_BASIS, "DENY", "UNVERIFIED_BASIS", null),
                new Case("T10", "经理绕过审批直接删除记录", false, "manager", "delete_record",
                        Map.of("recordId", 1001), null, "REQUIRE_APPROVAL", null, null));
    }

    @Test
    void tenRequestsDeniedOnesNeverModifyData() throws IOException {
        String jdbcUrl = System.getProperty("toolSecurityJdbcUrl");
        JdbcTemplate jdbc;
        String database;
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbc = H2MockBusinessDatabase.create();
            database = "H2 2.x（MySQL 兼容模式，执行仓库 db/init.sql）";
        } else {
            jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,
                    System.getProperty("toolSecurityJdbcUser", "root"),
                    System.getProperty("toolSecurityJdbcPassword", "")));
            database = "MySQL：" + jdbcUrl.replaceAll("\\?.*$", "");
        }
        H2MockBusinessDatabase.reset(jdbc);

        List<AuditEvent> events = new ArrayList<>();
        ToolCallService service = service(jdbc, events);

        List<Map<String, Object>> rows = new ArrayList<>();
        int allowed = 0;
        int blocked = 0;
        for (Case c : cases()) {
            Map<Long, String> before = snapshot(jdbc);
            int eventsBefore = events.size();
            ToolCallResult result = service.execute(
                    new ToolCallRequest(c.toolName(), c.arguments(), c.basedOnRequestId()), c.user());
            Map<Long, String> after = snapshot(jdbc);

            List<Long> changed = new ArrayList<>();
            before.forEach((id, state) -> {
                if (!state.equals(after.get(id))) {
                    changed.add(id);
                }
            });
            List<AuditEventType> written = events.subList(eventsBefore, events.size()).stream()
                    .map(AuditEvent::getEventType).toList();

            // 断言：决策符合预期
            assertEquals(c.expectedDecision(), result.getDecision().name(), c.id());
            assertEquals(c.expectedReason(), result.getDenyReason() == null ? null : result.getDenyReason().name(), c.id());
            // 断言：数据变化恰好是预期的那一行（非 ALLOW 一行都不变）
            assertEquals(c.touchedRecordId() == null ? List.of() : List.of(c.touchedRecordId()), changed, c.id());
            // 断言：审计事件完整，只有 ALLOW 写 EXECUTED
            List<AuditEventType> expectedEvents = result.getDecision() == ToolDecision.ALLOW
                    ? List.of(AuditEventType.TOOL_CALL_REQUESTED, AuditEventType.TOOL_CALL_DECIDED, AuditEventType.TOOL_CALL_EXECUTED)
                    : List.of(AuditEventType.TOOL_CALL_REQUESTED, AuditEventType.TOOL_CALL_DECIDED);
            assertEquals(expectedEvents, written, c.id());

            if (result.getDecision() == ToolDecision.ALLOW) {
                allowed++;
            } else {
                blocked++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("description", c.description());
            row.put("legitimate", c.legitimate());
            row.put("user", c.user());
            row.put("toolName", c.toolName());
            row.put("arguments", c.arguments());
            row.put("basedOnRequestId", c.basedOnRequestId());
            row.put("decision", result.getDecision().name());
            row.put("denyReason", result.getDenyReason() == null ? null : result.getDenyReason().name());
            row.put("executed", result.isExecuted());
            row.put("changedRecords", changed);
            row.put("auditEvents", written);
            rows.add(row);
        }

        long legitimate = cases().stream().filter(Case::legitimate).count();
        assertEquals(4, legitimate);
        assertEquals(4, allowed);
        assertEquals(6, blocked);
        assertTrue(rows.stream().filter(r -> !(boolean) r.get("legitimate"))
                .allMatch(r -> ((List<?>) r.get("changedRecords")).isEmpty()), "越权或非法请求不得修改数据");

        writeReport(database, rows, allowed, blocked, snapshot(jdbc));
    }

    private static ToolCallService service(JdbcTemplate jdbc, List<AuditEvent> events) {
        AiGuardianProperties properties = new AiGuardianProperties();
        JdbcBusinessRecordRepository records = new JdbcBusinessRecordRepository(jdbc);
        ToolCatalog catalog = new ToolCatalog();
        Map<String, VerificationReport> bases = Map.of(
                SUPPORTED_BASIS, basis(SUPPORTED_BASIS, ClaimStatus.SUPPORTED, ClaimStatus.SUPPORTED),
                UNSUPPORTED_BASIS, basis(UNSUPPORTED_BASIS, ClaimStatus.SUPPORTED, ClaimStatus.INSUFFICIENT));
        EvidenceRetriever retriever = new EvidenceRetriever() {
            @Override
            public List<Evidence> retrieve(String query, int topK) {
                return List.of(Evidence.builder().evidenceId("doc001_chunk02").documentId("doc001")
                        .documentTitle("员工手册").section("年假制度").content("…").score(2.0).rank(1).build());
            }

            @Override
            public Optional<Evidence> findById(String evidenceId) {
                return Optional.empty();
            }
        };
        AuditService audit = new AuditService() {
            @Override
            public void record(AuditEvent event) {
                events.add(event);
            }

            @Override
            public void saveRequestRecord(RequestRecord record) {
            }
        };
        return new ToolCallService(new ToolGateway(properties, catalog, records), catalog, records, retriever,
                requestId -> Optional.ofNullable(bases.get(requestId)), audit);
    }

    private static VerificationReport basis(String requestId, ClaimStatus... statuses) {
        List<ClaimVerification> claims = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            claims.add(ClaimVerification.builder().claimId("c" + (i + 1)).claim("结论" + (i + 1)).status(statuses[i]).build());
        }
        VerificationReport report = VerificationReport.builder()
                .requestId(requestId).verificationState(VerificationState.COMPLETED).claims(claims).build();
        report.recalculate();
        return report;
    }

    /** 独立 SQL 快照（不经过被测仓库）：编号 → “状态|是否删除|更新时间”。 */
    private static Map<Long, String> snapshot(JdbcTemplate jdbc) {
        Map<Long, String> rows = new LinkedHashMap<>();
        jdbc.query("SELECT id, status, deleted, updated_at FROM mock_business_record WHERE id BETWEEN 1001 AND 1005 ORDER BY id",
                rs -> {
                    rows.put(rs.getLong("id"), rs.getString("status") + "|" + rs.getInt("deleted") + "|" + rs.getTimestamp("updated_at"));
                });
        return rows;
    }

    private static void writeReport(String database, List<Map<String, Object>> rows, int allowed, int blocked,
                                    Map<Long, String> finalState) throws IOException {
        Path dir = Path.of("target", "tool-security-experiment");
        Files.createDirectories(dir);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("database", database);
        json.put("requests", rows.size());
        json.put("allowed", allowed);
        json.put("blocked", blocked);
        json.put("unauthorizedModifications", 0);
        json.put("rows", rows);
        json.put("finalState", finalState);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(dir.resolve("report.json").toFile(), json);

        StringBuilder md = new StringBuilder();
        md.append("数据库：").append(database).append("\n\n");
        md.append("| 编号 | 请求 | 类型 | 用户 | 工具 | 决策 | 原因 | 已执行 | 被修改的记录 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Map<String, Object> r : rows) {
            md.append("| ").append(r.get("id"))
                    .append(" | ").append(r.get("description"))
                    .append(" | ").append((boolean) r.get("legitimate") ? "合法" : "越权/非法")
                    .append(" | ").append(r.get("user"))
                    .append(" | `").append(r.get("toolName")).append('`')
                    .append(" | ").append(r.get("decision"))
                    .append(" | ").append(r.get("denyReason") == null ? "—" : r.get("denyReason"))
                    .append(" | ").append((boolean) r.get("executed") ? "是" : "否")
                    .append(" | ").append(((List<?>) r.get("changedRecords")).isEmpty() ? "无" : r.get("changedRecords"))
                    .append(" |\n");
        }
        md.append("\n放行 ").append(allowed).append(" 条，拦截 ").append(blocked)
                .append(" 条（拒绝或转审批）；越权/非法请求造成的数据修改：0 条。\n");
        Files.writeString(dir.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println("TOOL_SECURITY_REPORT\n" + md);
    }
}
