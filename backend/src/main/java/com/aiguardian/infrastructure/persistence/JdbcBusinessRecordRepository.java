package com.aiguardian.infrastructure.persistence;

import com.aiguardian.domain.model.BusinessRecord;
import com.aiguardian.domain.tool.BusinessRecordRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** mock_business_record 的 JDBC 实现（契约 9，P1）。只用标准 SQL，MySQL 与测试用的 H2 都可运行。 */
@Repository
@Profile("!stub")
public class JdbcBusinessRecordRepository implements BusinessRecordRepository {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String COLUMNS = "id, title, owner, status, deleted, updated_at";

    private final JdbcTemplate jdbc;

    public JdbcBusinessRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BusinessRecord> findById(long recordId) {
        List<BusinessRecord> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM mock_business_record WHERE id = ?",
                (rs, rowNum) -> map(rs),
                recordId);
        return rows.stream().findFirst();
    }

    @Override
    public List<BusinessRecord> findAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM mock_business_record ORDER BY id",
                (rs, rowNum) -> map(rs));
    }

    @Override
    public int updateStatus(long recordId, String status, OffsetDateTime now) {
        return jdbc.update(
                "UPDATE mock_business_record SET status = ?, updated_at = ? WHERE id = ? AND deleted = 0",
                status,
                toTimestamp(now),
                recordId);
    }

    @Override
    public int softDelete(long recordId, OffsetDateTime now) {
        return jdbc.update(
                "UPDATE mock_business_record SET deleted = 1, updated_at = ? WHERE id = ? AND deleted = 0",
                toTimestamp(now),
                recordId);
    }

    private static BusinessRecord map(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return BusinessRecord.builder()
                .recordId(rs.getLong("id"))
                .title(rs.getString("title"))
                .owner(rs.getString("owner"))
                .status(rs.getString("status"))
                .deleted(rs.getBoolean("deleted"))
                .updatedAt(updatedAt.toLocalDateTime().atZone(SHANGHAI).toOffsetDateTime().toString())
                .build();
    }

    private static Timestamp toTimestamp(OffsetDateTime value) {
        return Timestamp.valueOf(value.atZoneSameInstant(SHANGHAI).toLocalDateTime());
    }
}
