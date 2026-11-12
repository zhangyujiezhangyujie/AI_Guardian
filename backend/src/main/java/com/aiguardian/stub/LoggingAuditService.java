package com.aiguardian.stub;

import com.aiguardian.audit.AuditService;
import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.RequestRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("stub")
public class LoggingAuditService implements AuditService {
    @Override
    public void record(AuditEvent event) {
        log.info("requestId={} event={} success={} durationMs={} errorCode={} detail={}",
                event.getRequestId(), event.getEventType(), event.isSuccess(), event.getDurationMs(),
                event.getErrorCode(), event.getDetail());
    }

    @Override
    public void saveRequestRecord(RequestRecord record) {
        log.info("requestId={} sessionId={} mode={} errorCode={} timing={}", record.getRequestId(),
                record.getSessionId(), record.getMode(), record.getErrorCode(), record.getTiming());
    }
}
