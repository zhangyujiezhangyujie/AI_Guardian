package com.aiguardian.domain.guard;

import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.GuardResult;
import com.aiguardian.domain.model.VerificationReport;
import org.springframework.stereotype.Component;

@Component
public class AnnotateOutputGuard implements OutputGuard {
    static final String NO_EVIDENCE = "知识库中没有找到与该问题相关的资料，系统未生成回答。";
    static final String ALL_SUPPORTED = "回答中的全部结论均有知识库证据支持。";
    static final String INSUFFICIENT = "部分结论未在知识库中找到充分证据，已用黄色标出。";
    static final String TRUNCATED = "回答包含的结论较多，仅核验了前 8 条，其余内容未经核验。";
    static final String FAILED_VISIBLE = "核验未完成，以下回答未经证据核验，请谨慎参考。";
    static final String FAILED_HIDDEN = "核验未完成，为避免输出未经核验的内容，系统已暂停展示回答。";
    static final String NO_CLAIMS = "回答中没有需要核验的事实性结论。";
    static final String BLOCKED = "当前回答中有较多结论缺乏充分证据，系统已限制输出，请核对原始资料。";

    @Override
    public String name() {
        return "annotate";
    }

    @Override
    public GuardResult evaluate(VerificationReport report, GuardPolicy policy) {
        return switch (report.getVerificationState()) {
            case NO_EVIDENCE -> result(report, policy, GuardAction.NO_EVIDENCE, true, NO_EVIDENCE, null);
            case FAILED -> result(report, policy, GuardAction.NOT_VERIFIED, true, report.getAnswer(), FAILED_VISIBLE);
            case NO_CLAIMS -> result(report, policy, GuardAction.NO_CLAIMS, true, report.getAnswer(), NO_CLAIMS);
            case COMPLETED, PARTIAL -> result(report, policy, GuardAction.ANNOTATED, true, report.getAnswer(),
                    annotationMessage(report));
        };
    }

    private static String annotationMessage(VerificationReport report) {
        if (report.isTruncated()) {
            return TRUNCATED;
        }
        return report.getInsufficientClaims() == 0 ? ALL_SUPPORTED : INSUFFICIENT;
    }

    static GuardResult result(VerificationReport report, GuardPolicy policy, GuardAction action,
                              boolean visible, String displayAnswer, String message) {
        return GuardResult.builder()
                .strategy(policy.getStrategy())
                .blockThreshold(policy.getBlockThreshold())
                .action(action)
                .answerVisible(visible)
                .displayAnswer(displayAnswer)
                .message(message)
                .unsupportedRatio(report.getUnsupportedRatio())
                .build();
    }
}
