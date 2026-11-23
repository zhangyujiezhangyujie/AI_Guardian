-- 把模拟业务记录 1001–1005 恢复为初始状态（契约 v1.3 第 9 章），用于重复演示和安全实验。
-- 只影响 mock_business_record，不触碰问答与审计数据。
USE ai_guardian;

DELETE FROM mock_business_record WHERE id BETWEEN 1001 AND 1005;

INSERT INTO mock_business_record (id, title, owner, status, deleted, updated_at) VALUES
    (1001, '客户合同归档（华东区）', 'user',    'OPEN',     0, '2026-11-22 10:00:00.000'),
    (1002, '季度采购对账单',         'user',    'OPEN',     0, '2026-11-22 10:00:00.000'),
    (1003, '供应商资质复核',         'manager', 'OPEN',     0, '2026-11-22 10:00:00.000'),
    (1004, '客户投诉处理记录',       'user',    'CLOSED',   0, '2026-11-22 10:00:00.000'),
    (1005, '年度设备盘点表',         'manager', 'ARCHIVED', 0, '2026-11-22 10:00:00.000');
