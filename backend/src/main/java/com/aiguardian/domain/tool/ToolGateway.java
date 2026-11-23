package com.aiguardian.domain.tool;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.model.ToolCallRequest;
import com.aiguardian.domain.model.ToolDecision;
import com.aiguardian.domain.model.ToolDenyReason;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 工具调用安全网关的决策链（契约 7.10）：
 * 白名单 → 权限 → 参数 → 记录存在 → 审批 → 依据核验。第一个不通过即停止。
 *
 * <p>只做判断，不执行工具、不写审计；不调用大模型，判断完全由确定性代码完成。
 * 依赖数据库中的模拟业务记录，因此与其他持久化组件一样不在 stub 模式中加载。
 */
@Component
@Profile("!stub")
public class ToolGateway {

    static final String MSG_ALLOW = "已通过网关检查并执行。";

    static final String MSG_APPROVAL = "该操作需要部门经理审批，网关未执行。";

    private static final Map<ToolDenyReason, String> DENY_MESSAGES = Map.of(
            ToolDenyReason.TOOL_NOT_ALLOWED, "工具不在白名单中，已拒绝。",
            ToolDenyReason.PERMISSION_DENIED, "当前用户无权使用该工具，已拒绝。",
            ToolDenyReason.INVALID_ARGUMENTS, "工具参数不符合约束，已拒绝。",
            ToolDenyReason.RECORD_NOT_FOUND, "目标业务记录不存在，已拒绝。",
            ToolDenyReason.BASIS_NOT_FOUND, "依据的问答记录不存在，已拒绝。",
            ToolDenyReason.UNVERIFIED_BASIS, "该操作依据的回答存在未经证据支持的结论，已拒绝。");

    private final AiGuardianProperties properties;

    private final ToolCatalog catalog;

    private final BusinessRecordRepository records;

    public ToolGateway(AiGuardianProperties properties, ToolCatalog catalog, BusinessRecordRepository records) {
        this.properties = properties;
        this.catalog = catalog;
        this.records = records;
    }

    /**
     * @param request 工具调用请求
     * @param user    演示用户（user / manager），调用方已校验
     * @param basis   依据报告的延迟查询；只在第 5 步需要时调用，empty 表示依据记录不存在
     */
    public ToolDecisionOutcome decide(ToolCallRequest request, String user,
                                      Supplier<Optional<VerificationReport>> basis) {
        String toolName = request.getToolName();
        AiGuardianProperties.ToolGateway config = properties.getToolGateway();

        // 1. 白名单
        if (!catalog.isKnown(toolName) || !config.getAllowedTools().contains(toolName)) {
            return deny(ToolDenyReason.TOOL_NOT_ALLOWED, null);
        }

        // 2. 权限
        List<String> allowedForRole = config.getRoles().getOrDefault(user, List.of());
        if (!allowedForRole.contains(toolName)) {
            return deny(ToolDenyReason.PERMISSION_DENIED, null);
        }

        // 3. 参数
        Optional<ToolArguments> validated = catalog.validate(toolName, request.getArguments());
        if (validated.isEmpty()) {
            return deny(ToolDenyReason.INVALID_ARGUMENTS, null);
        }
        ToolArguments arguments = validated.get();

        // 3'. 记录存在且未删除
        if (catalog.isRecordTool(toolName)) {
            boolean exists = records.findById(arguments.recordId())
                    .filter(record -> !record.isDeleted())
                    .isPresent();
            if (!exists) {
                return deny(ToolDenyReason.RECORD_NOT_FOUND, arguments);
            }
        }

        // 4. 审批
        if (config.getApprovalRequired().contains(toolName)) {
            return new ToolDecisionOutcome(ToolDecision.REQUIRE_APPROVAL, null, MSG_APPROVAL, arguments);
        }

        // 5. 依据核验：写操作不得依赖未经证据支持的结论
        boolean strict = properties.getOutputGuard().isStrictForToolCall();
        if (strict && catalog.isWrite(toolName) && request.getBasedOnRequestId() != null) {
            Optional<VerificationReport> report = basis.get();
            if (report.isEmpty()) {
                return deny(ToolDenyReason.BASIS_NOT_FOUND, arguments);
            }
            if (!fullySupported(report.get())) {
                return deny(ToolDenyReason.UNVERIFIED_BASIS, arguments);
            }
        }

        return new ToolDecisionOutcome(ToolDecision.ALLOW, null, MSG_ALLOW, arguments);
    }

    /** 依据可用：核验完成、至少一条结论、全部有证据。PARTIAL、FAILED、NO_CLAIMS、NO_EVIDENCE 都不可用。 */
    static boolean fullySupported(VerificationReport report) {
        return report.getVerificationState() == VerificationState.COMPLETED
                && report.getTotalClaims() >= 1
                && report.getInsufficientClaims() == 0;
    }

    private static ToolDecisionOutcome deny(ToolDenyReason reason, ToolArguments arguments) {
        return new ToolDecisionOutcome(ToolDecision.DENY, reason, DENY_MESSAGES.get(reason), arguments);
    }
}
