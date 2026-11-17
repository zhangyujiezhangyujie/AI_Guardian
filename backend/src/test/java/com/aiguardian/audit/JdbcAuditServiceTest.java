package com.aiguardian.audit;

import com.aiguardian.domain.model.AuditEvent;
import com.aiguardian.domain.model.AuditEventType;
import com.aiguardian.infrastructure.persistence.JdbcAuditPersistenceRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcAuditServiceTest {
    @Test
    void auditEventDetailDropsKeysOutsideTheContract() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAuditPersistenceRepository repository = new JdbcAuditPersistenceRepository(jdbc,
                new com.fasterxml.jackson.databind.ObjectMapper());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mode", "MOCK");
        detail.put("scenarioId", "SC01");
        detail.put("questionLength", 18);
        detail.put("apiKey", "token-secret");
        detail.put("prompt", "private prompt");
        AuditEvent event = AuditEvent.builder()
                .requestId("req_123456789abc")
                .eventType(AuditEventType.REQUEST_RECEIVED)
                .success(true)
                .detail(detail)
                .createdAt(OffsetDateTime.parse("2026-11-15T10:00:00+08:00"))
                .build();

        repository.insertEvent(event);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO audit_event"), arguments.capture());
        String json = (String) arguments.getValue()[5];
        assertTrue(json.contains("\"mode\":\"MOCK\""));
        assertFalse(json.contains("token-secret"));
        assertFalse(json.contains("private prompt"));
    }

    @Test
    void auditWriteFailureDoesNotEscapeToTheCaller() {
        JdbcAuditPersistenceRepository repository = mock(JdbcAuditPersistenceRepository.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).insertEvent(any(AuditEvent.class));
        JdbcAuditService service = new JdbcAuditService(repository);
        AuditEvent event = AuditEvent.builder()
                .requestId("req_123456789abc")
                .eventType(AuditEventType.REQUEST_RECEIVED)
                .success(true)
                .createdAt(OffsetDateTime.parse("2026-11-15T10:00:00+08:00"))
                .build();

        assertDoesNotThrow(() -> service.record(event));
    }
}
