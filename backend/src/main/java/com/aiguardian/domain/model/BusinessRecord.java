package com.aiguardian.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模拟业务记录，对应表 mock_business_record（契约 3.11、9，P1）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessRecord {
    private long recordId;
    private String title;
    private String owner;
    private String status;
    private boolean deleted;
    private String updatedAt;
}
