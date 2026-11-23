package com.aiguardian.domain.tool;

import com.aiguardian.domain.model.BusinessRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模拟业务记录仓库（契约 9：mock_business_record，P1）。
 * 接口在 domain，JDBC 实现在 infrastructure/persistence。
 */
public interface BusinessRecordRepository {

    /** 按编号查询，包括已软删除的记录；不存在返回 empty。 */
    Optional<BusinessRecord> findById(long recordId);

    /** 全部记录，按编号升序（用于安全实验对比修改前后）。 */
    List<BusinessRecord> findAll();

    /** 修改未删除记录的状态，返回受影响行数。 */
    int updateStatus(long recordId, String status, OffsetDateTime now);

    /** 软删除未删除的记录，返回受影响行数。 */
    int softDelete(long recordId, OffsetDateTime now);
}
