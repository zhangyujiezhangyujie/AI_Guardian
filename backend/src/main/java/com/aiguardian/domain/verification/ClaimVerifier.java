package com.aiguardian.domain.verification;

import com.aiguardian.domain.model.VerificationInput;
import com.aiguardian.domain.model.VerificationReport;

public interface ClaimVerifier {
    /** 返回初判报告，模型或结构错误以 FAILED 报告表示。 */
    VerificationReport verify(VerificationInput input);

    String name();
}
