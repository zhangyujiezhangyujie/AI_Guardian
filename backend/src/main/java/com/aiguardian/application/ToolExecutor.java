package com.aiguardian.application;

import com.aiguardian.common.ShanghaiTime;
import com.aiguardian.domain.evidence.EvidenceRetriever;
import com.aiguardian.domain.model.BusinessRecord;
import com.aiguardian.domain.model.Evidence;
import com.aiguardian.domain.tool.BusinessRecordRepository;
import com.aiguardian.domain.tool.ToolArguments;
import com.aiguardian.domain.tool.ToolCatalog;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 工具的实际执行与记录快照（契约 7.10）。只在网关放行、或审批通过且重新检查通过后调用。
 */
@Slf4j
@Component
@Profile("!stub")
public class ToolExecutor {

    private static final int SEARCH_TOP_K = 3;

    private final ToolCatalog catalog;

    private final BusinessRecordRepository records;

    private final EvidenceRetriever retriever;

    public ToolExecutor(ToolCatalog catalog, BusinessRecordRepository records, EvidenceRetriever retriever) {
        this.catalog = catalog;
        this.records = records;
        this.retriever = retriever;
    }

    /** 执行工具，返回 result（键见契约 7.10）；失败抛出异常，由调用方记录审计。 */
    public Map<String, Object> execute(String toolName, ToolArguments arguments) {
        OffsetDateTime now = ShanghaiTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        switch (toolName) {
            case ToolCatalog.SEARCH_DOCUMENT -> {
                List<Map<String, Object>> hits = new ArrayList<>();
                for (Evidence evidence : retriever.retrieve(arguments.keyword(), SEARCH_TOP_K)) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("evidenceId", evidence.getEvidenceId());
                    hit.put("documentTitle", evidence.getDocumentTitle());
                    hit.put("section", evidence.getSection());
                    hits.add(hit);
                }
                result.put("hits", hits);
            }
            case ToolCatalog.UPDATE_RECORD -> {
                String before = records.findById(arguments.recordId())
                        .map(BusinessRecord::getStatus)
                        .orElse(null);
                requireOneRow(records.updateStatus(arguments.recordId(), arguments.status(), now));
                result.put("recordId", arguments.recordId());
                result.put("statusBefore", before);
                result.put("statusAfter", arguments.status());
            }
            case ToolCatalog.DELETE_RECORD -> {
                requireOneRow(records.softDelete(arguments.recordId(), now));
                result.put("recordId", arguments.recordId());
            }
            default -> throw new IllegalStateException("未知工具：" + toolName);
        }
        return result;
    }

    /**
     * 记录类工具：读取目标记录当前状态，展示数据是否被修改（契约 3.11 record）。
     * 这只是展示信息：读取失败（如数据库不可用）时返回 null 并记录日志，不影响已经做出的决策。
     */
    public BusinessRecord currentRecord(String toolName, Map<String, Object> arguments) {
        if (!catalog.isRecordTool(toolName) || arguments == null) {
            return null;
        }
        Object rawId = arguments.get("recordId");
        if (!(rawId instanceof Integer) && !(rawId instanceof Long)) {
            return null;
        }
        try {
            return records.findById(((Number) rawId).longValue()).orElse(null);
        } catch (RuntimeException e) {
            log.error("recordId={} recordSnapshotFailed exceptionType={}", rawId, e.getClass().getName());
            return null;
        }
    }

    private static void requireOneRow(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("预期修改 1 行，实际 " + rows + " 行");
        }
    }
}
