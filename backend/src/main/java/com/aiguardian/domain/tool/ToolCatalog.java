package com.aiguardian.domain.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 三个模拟工具的定义与参数约束（契约 7.10 工具清单）。
 * 白名单、角色与审批规则来自配置，不在这里。
 */
@Component
public class ToolCatalog {

    public static final String SEARCH_DOCUMENT = "search_document";

    public static final String UPDATE_RECORD = "update_record";

    public static final String DELETE_RECORD = "delete_record";

    public static final List<String> RECORD_STATUSES = List.of("OPEN", "CLOSED", "ARCHIVED");

    private static final int KEYWORD_MAX_LENGTH = 50;

    private static final Map<String, Set<String>> PARAMETERS = Map.of(
            SEARCH_DOCUMENT, Set.of("keyword"),
            UPDATE_RECORD, Set.of("recordId", "status"),
            DELETE_RECORD, Set.of("recordId"));

    /** 工具是否在清单中（与白名单无关）。 */
    public boolean isKnown(String toolName) {
        return toolName != null && PARAMETERS.containsKey(toolName);
    }

    /** 是否为写操作（决策链第 5 步只检查写操作）。 */
    public boolean isWrite(String toolName) {
        return UPDATE_RECORD.equals(toolName) || DELETE_RECORD.equals(toolName);
    }

    /** 是否以 recordId 指向一条业务记录。 */
    public boolean isRecordTool(String toolName) {
        return isWrite(toolName);
    }

    /**
     * 校验参数：字段必须恰好是清单中的字段，类型与取值符合约束。
     * 不通过返回 empty（决策为 INVALID_ARGUMENTS）。
     */
    public Optional<ToolArguments> validate(String toolName, Map<String, Object> arguments) {
        if (!isKnown(toolName) || arguments == null) {
            return Optional.empty();
        }
        if (!arguments.keySet().equals(PARAMETERS.get(toolName))) {
            return Optional.empty();
        }
        return switch (toolName) {
            case SEARCH_DOCUMENT -> keyword(arguments.get("keyword"))
                    .map(keyword -> new ToolArguments(keyword, null, null));
            case UPDATE_RECORD -> {
                Optional<Long> recordId = recordId(arguments.get("recordId"));
                Optional<String> status = status(arguments.get("status"));
                yield recordId.isPresent() && status.isPresent()
                        ? Optional.of(new ToolArguments(null, recordId.get(), status.get()))
                        : Optional.empty();
            }
            case DELETE_RECORD -> recordId(arguments.get("recordId"))
                    .map(recordId -> new ToolArguments(null, recordId, null));
            default -> Optional.empty();
        };
    }

    private static Optional<String> keyword(Object value) {
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        String keyword = text.strip();
        if (keyword.isEmpty() || keyword.length() > KEYWORD_MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(keyword);
    }

    /** 只接受 JSON 整数（不接受字符串 "1001" 或小数），且必须为正数。 */
    private static Optional<Long> recordId(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            return Optional.empty();
        }
        long id = ((Number) value).longValue();
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    private static Optional<String> status(Object value) {
        if (value instanceof String text && RECORD_STATUSES.contains(text)) {
            return Optional.of(text);
        }
        return Optional.empty();
    }
}
