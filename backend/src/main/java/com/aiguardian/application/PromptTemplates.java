package com.aiguardian.application;

import com.aiguardian.domain.evidence.EvidenceFormatter;
import com.aiguardian.domain.model.Evidence;
import java.util.List;

public final class PromptTemplates {
    public static final String GENERATE_SYSTEM_PROMPT = """
            你是某企业的内部制度问答助手。你只能依据“参考资料”回答员工的问题。
            规则：
            1. 只使用参考资料中明确写出的信息，不要补充资料以外的数字、金额、天数、政策或流程。
            2. 如果参考资料不足以回答问题的某一部分，直接说明“资料中未找到相关规定”。
            3. 回答使用简体中文，控制在 200 字以内，不使用 Markdown 标题和列表符号。
            4. 不要在回答中输出证据编号。
            """;

    private PromptTemplates() {}

    public static String generateUserPrompt(String question, List<Evidence> evidence) {
        return "参考资料：\n%s\n\n员工问题：%s".formatted(EvidenceFormatter.format(evidence), question);
    }
}
