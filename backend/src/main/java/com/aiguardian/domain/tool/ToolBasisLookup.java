package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.VerificationReport;
import java.util.Optional;

/** 按 requestId 查询工具调用所依据的核验报告（未遮蔽）；不存在返回 empty（契约 7.10 第 5 步）。 */
public interface ToolBasisLookup {
    Optional<VerificationReport> find(String requestId);
}
