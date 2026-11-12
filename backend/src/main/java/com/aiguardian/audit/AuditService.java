package com.aiguardian.audit;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.RequestRecord;

public interface AuditService {
    /** 记录一步审计事件，写入失败由实现记录日志。 */
    void record(AuditEvent event);

    /** 保存原始请求记录，写入失败由实现记录日志。 */
    void saveRequestRecord(RequestRecord record);
}
