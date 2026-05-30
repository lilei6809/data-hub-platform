-- ============================================================================
-- V2: 补齐 tenant_iam_provisioning_state 与领域模型 TenantIamProvisioningState
--     之间的字段差异
--
-- 背景:
--   V1 建表时只覆盖了宏观状态机 + 重试治理的核心字段。
--   随着领域模型演进,新增了以下关注点:
--     1. 乐观锁 version — 多 Pod 并发写入的正确性保证
--     2. 四个 checkpoint 布尔 — Step Pipeline 子目标达成状态
--     3. failed_at — 区分"最后一次尝试时间"和"确认终态失败时间"
--     4. next_retry_at — RetryScheduler 轮询的调度锚点
--
-- 对应领域模型: TenantIamProvisioningState.java
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 乐观锁版本号
--    用途: save() 时 UPDATE ... WHERE version = :expected,
--          affectedRows == 0 → 抛 ConcurrencyException
--    DEFAULT 0: 与领域模型 init() 的初始值对齐
-- ----------------------------------------------------------------------------
ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------------------
-- 2. Step Pipeline checkpoint 布尔字段
--    用途: 记录每个 ensure 步骤是否已成功完成,
--          markCompleted() 要求四个全为 true 才允许推进到 IAM_COMPLETED
--    命名: 与 TenantIamProvisioningCheckpoint 枚举一一对应
-- ----------------------------------------------------------------------------
ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN keycloak_organization_created BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN admin_user_created BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN default_roles_assigned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN admin_user_membership_created BOOLEAN NOT NULL DEFAULT FALSE;

-- ----------------------------------------------------------------------------
-- 3. 终态失败时间戳
--    用途: 仅在 markFailed() 时设置,区别于 last_attempt_at
--          (last_attempt_at 在每次尝试时都更新,无论成功失败;
--           failed_at 只在确认不可重试的终态失败时才设置)
-- ----------------------------------------------------------------------------
ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN failed_at TIMESTAMPTZ;

-- ----------------------------------------------------------------------------
-- 4. 下次重试时间点
--    用途: markAwaitRetry() 时根据指数退避计算并写入,
--          RetryScheduler 用 WHERE next_retry_at <= NOW() 轮询
--    NULL 语义: NULL 表示不需要等待重试(PENDING / IN_PROGRESS / COMPLETED / FAILED)
-- ----------------------------------------------------------------------------
ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN next_retry_at TIMESTAMPTZ;

-- ----------------------------------------------------------------------------
-- 5. RetryScheduler 查询索引
--    覆盖 findReadyForRetry(now, limit) 的查询模式:
--      WHERE iam_status = 'IAM_AWAITING_RETRY' AND next_retry_at <= :now
--      ORDER BY next_retry_at ASC LIMIT :limit
--      FOR UPDATE SKIP LOCKED
--
--    为什么用组合索引而不是单列:
--      单独索引 iam_status 选择性太低(大量 COMPLETED 记录);
--      组合 (iam_status, next_retry_at) 让 PostgreSQL 先缩小到 AWAITING_RETRY,
--      再在其中按时间范围扫描,高效且天然有序。
-- ----------------------------------------------------------------------------
CREATE INDEX idx_provisioning_state_retry_schedule
    ON tenant_iam_provisioning_state (iam_status, next_retry_at)
    WHERE iam_status = 'IAM_AWAITING_RETRY';