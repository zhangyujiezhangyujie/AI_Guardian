package com.aiguardian.audit;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.RequestRecord;
import com.aiguardian.infrastructure.persistence.JdbcAuditPersistenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!stub")
@RequiredArgsConstructor
public class JdbcAuditService implements AuditService {
    private final JdbcAuditPersistenceRepository repository;

    @Override
    public void record(AuditEvent event) {
        try {
            repository.insertEvent(event);
        } catch (RuntimeException e) {
            log.error("requestId={} auditEventWriteFailed exceptionType={}",
                    event == null ? null : event.getRequestId(), e.getClass().getName());
        }
    }

    @Override
    public void saveRequestRecord(RequestRecord record) {
        try {
            repository.insertRequestRecord(record);
        } catch (RuntimeException e) {
            log.error("requestId={} auditRecordWriteFailed exceptionType={}",
                    record == null ? null : record.getRequestId(), e.getClass().getName());
        }
    }
}
