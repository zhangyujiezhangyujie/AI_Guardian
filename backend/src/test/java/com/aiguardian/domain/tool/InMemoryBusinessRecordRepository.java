package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.BusinessRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 测试用内存仓库：初始数据与 db/init.sql 的 1001–1005 一致。 */
public class InMemoryBusinessRecordRepository implements BusinessRecordRepository {

    private static final String INITIAL_TIME = "2026-11-22T10:00+08:00";

    private final Map<Long, BusinessRecord> rows = new TreeMap<>();

    private int writes;

    public InMemoryBusinessRecordRepository() {
        put(1001, "客户合同归档（华东区）", "user", "OPEN");
        put(1002, "季度采购对账单", "user", "OPEN");
        put(1003, "供应商资质复核", "manager", "OPEN");
        put(1004, "客户投诉处理记录", "user", "CLOSED");
        put(1005, "年度设备盘点表", "manager", "ARCHIVED");
    }

    private void put(long id, String title, String owner, String status) {
        rows.put(id, new BusinessRecord(id, title, owner, status, false, INITIAL_TIME));
    }

    @Override
    public Optional<BusinessRecord> findById(long recordId) {
        return Optional.ofNullable(rows.get(recordId)).map(InMemoryBusinessRecordRepository::copy);
    }

    @Override
    public List<BusinessRecord> findAll() {
        List<BusinessRecord> all = new ArrayList<>();
        rows.values().forEach(record -> all.add(copy(record)));
        return all;
    }

    @Override
    public int updateStatus(long recordId, String status, OffsetDateTime now) {
        BusinessRecord record = rows.get(recordId);
        if (record == null || record.isDeleted()) {
            return 0;
        }
        writes++;
        record.setStatus(status);
        record.setUpdatedAt(now.toString());
        return 1;
    }

    @Override
    public int softDelete(long recordId, OffsetDateTime now) {
        BusinessRecord record = rows.get(recordId);
        if (record == null || record.isDeleted()) {
            return 0;
        }
        writes++;
        record.setDeleted(true);
        record.setUpdatedAt(now.toString());
        return 1;
    }

    /** 实际发生的写入次数（用于断言“拒绝时没有写入”）。 */
    public int writes() {
        return writes;
    }

    private static BusinessRecord copy(BusinessRecord r) {
        return new BusinessRecord(r.getRecordId(), r.getTitle(), r.getOwner(), r.getStatus(), r.isDeleted(), r.getUpdatedAt());
    }
}
