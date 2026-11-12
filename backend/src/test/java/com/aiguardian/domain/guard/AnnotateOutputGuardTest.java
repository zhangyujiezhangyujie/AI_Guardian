package com.aiguardian.domain.guard;

import com.aiguardian.domain.model.GuardAction;
import com.aiguardian.domain.model.GuardPolicy;
import com.aiguardian.domain.model.VerificationReport;
import com.aiguardian.domain.model.VerificationState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotateOutputGuardTest {
    private final OutputGuard guard = new AnnotateOutputGuard();

    @ParameterizedTest
    @CsvSource(value = {
            "NO_EVIDENCE, NULL, 0, false, NO_EVIDENCE, true, NULL",
            "FAILED, NULL, 0, false, NOT_VERIFIED, true, 核验未完成，以下回答未经证据核验，请谨慎参考。",
            "NO_CLAIMS, NULL, 0, false, NO_CLAIMS, true, 回答中没有需要核验的事实性结论。",
            "COMPLETED, 0.5, 1, false, ANNOTATED, true, 部分结论未在知识库中找到充分证据，已用黄色标出。",
            "COMPLETED, 0.6667, 2, false, ANNOTATED, true, 部分结论未在知识库中找到充分证据，已用黄色标出。",
            "COMPLETED, 0.0, 0, false, ANNOTATED, true, 回答中的全部结论均有知识库证据支持。",
            "PARTIAL, 0.125, 1, true, ANNOTATED, true, 回答包含的结论较多，仅核验了前 8 条，其余内容未经核验。"
    }, nullValues = "NULL")
    void appliesContract(VerificationState state, Double ratio, int insufficient, boolean truncated,
                         GuardAction action, boolean visible, String message) {
        var report = VerificationReport.builder().answer("原始回答").verificationState(state)
                .unsupportedRatio(ratio).insufficientClaims(insufficient).truncated(truncated).build();
        var result = guard.evaluate(report, new GuardPolicy(guard.name(), 0.5));

        assertThat(result.getAction()).isEqualTo(action);
        assertThat(result.isAnswerVisible()).isEqualTo(visible);
        assertThat(result.getMessage()).isEqualTo(message);
        if (action == GuardAction.NO_EVIDENCE) {
            assertThat(result.getDisplayAnswer()).isEqualTo("知识库中没有找到与该问题相关的资料，系统未生成回答。");
        } else {
            assertThat(result.getDisplayAnswer()).isEqualTo(visible ? "原始回答" : null);
        }
        assertThat(result.getUnsupportedRatio()).isEqualTo(ratio);
    }
}
