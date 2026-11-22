package com.aiguardian.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiguardian.domain.model.BusinessRecord;
import com.aiguardian.infrastructure.persistence.JdbcBusinessRecordRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcBusinessRecordRepositoryTest {

    private JdbcTemplate jdbc;

    private JdbcBusinessRecordRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = H2MockBusinessDatabase.create();
        repository = new JdbcBusinessRecordRepository(jdbc);
    }

    @Test
    void initSqlSeedsFiveRecords() {
        List<BusinessRecord> all = repository.findAll();
        assertEquals(List.of(1001L, 1002L, 1003L, 1004L, 1005L), all.stream().map(BusinessRecord::getRecordId).toList());
        BusinessRecord first = all.get(0);
        assertEquals("客户合同归档（华东区）", first.getTitle());
        assertEquals("OPEN", first.getStatus());
        assertFalse(first.isDeleted());
        assertTrue(first.getUpdatedAt().startsWith("2026-11-22T10:00"));
    }

    @Test
    void updateAndSoftDeleteOnlyTouchLiveRows() {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 22, 12, 0, 0, 0, ZoneOffset.ofHours(8));
        assertEquals(1, repository.updateStatus(1002, "CLOSED", now));
        assertEquals("CLOSED", repository.findById(1002).orElseThrow().getStatus());

        assertEquals(1, repository.softDelete(1003, now));
        assertTrue(repository.findById(1003).orElseThrow().isDeleted());
        assertEquals(0, repository.softDelete(1003, now));
        assertEquals(0, repository.updateStatus(1003, "OPEN", now));

        assertEquals(0, repository.updateStatus(9999, "OPEN", now));
        assertTrue(repository.findById(9999).isEmpty());
    }

    @Test
    void resetScriptRestoresInitialState() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        repository.updateStatus(1001, "ARCHIVED", now);
        repository.softDelete(1002, now);
        H2MockBusinessDatabase.reset(jdbc);
        assertEquals("OPEN", repository.findById(1001).orElseThrow().getStatus());
        assertFalse(repository.findById(1002).orElseThrow().isDeleted());
    }

    @Test
    void rerunningInitSqlDoesNotDuplicateOrOverwrite() {
        repository.updateStatus(1001, "CLOSED", OffsetDateTime.now(ZoneOffset.ofHours(8)));
        H2MockBusinessDatabase.execute(jdbc, H2MockBusinessDatabase.read("db/init.sql")
                .substring(H2MockBusinessDatabase.read("db/init.sql").indexOf("-- P1 工具调用安全网关")));
        assertEquals(5, repository.findAll().size());
        assertEquals("CLOSED", repository.findById(1001).orElseThrow().getStatus());
    }
}
