CREATE TABLE outbox_events (
       event_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 用 gen_random_uuid() 让数据库自动生成，而不是应用层生成，原因是减少一次网络往返，并且 PostgreSQL 的 UUID 生成器是密码学安全的
       aggregate_type  VARCHAR(100) NOT NULL,
    -- aggregate_type 这个事件是关于哪个业务对象的？ TenantIamProvisioningState
       aggregate_id    VARCHAR(100) NOT NULL,
    -- tenant-id
       event_type      VARCHAR(150) NOT NULL,
    -- 值就是事件的类名，比如 TenantIamProvisionedEvent 或 TenantIamProvisioningFailedEvent
    -- Publisher 在构建 Kafka 消息时，会把这个值放进 header，下游消费者据此决定用哪个反序列化类来还原事件
       event_version   INT NOT NULL,
    -- schema evolution
       topic           VARCHAR(200) NOT NULL,
    -- topic name
       payload         JSONB NOT NULL,
       headers         JSONB NOT NULL,

       status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING（待发布）、PUBLISHED（已发布）、FAILED（发布失败且放弃重试）
       retry_count     INT NOT NULL DEFAULT 0,
       next_retry_at   TIMESTAMP WITH TIME ZONE,
       last_error      TEXT,

       correlation_id  VARCHAR(100) NOT NULL,
       causation_id    VARCHAR(100),
       occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    -- caused 的时间吗?
       created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- 这个时间戳是 outbox 记录写入数据库的时间，由 DEFAULT now() 自动填充
       published_at    TIMESTAMP WITH TIME ZONE,
   --Publisher 成功发送 Kafka 并收到 ACK 后，在把 status 更新为 PUBLISHED 的同时记录这个时间戳

       claimed_by     VARCHAR(128),
       claimed_at     TIMESTAMP WITH TIME ZONE,

        CONSTRAINT chk_status CHECK ( status IN ('PENDING', 'PUBLISHED', 'CLAIMING', 'FAILED') )
);

CREATE INDEX idx_outbox_status_next_retry
    ON outbox_events(status, created_at, next_retry_at);

CREATE INDEX idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_correlation
    ON outbox_events(correlation_id);

-- 为超时回收查询创建索引
-- 回收任务的 WHERE: status = 'CLAIMING' AND claimed_at < threshold
CREATE INDEX idx_outbox_claiming_stale
    ON outbox_events (claimed_at)
    WHERE status = 'CLAIMING';

-- 为 claimBatch 查询优化索引（如果尚未存在）
-- claimBatch 的 WHERE: status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= now())
CREATE INDEX idx_outbox_pending_ready
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

-- header: {
--   "eventId": "2f4a1e6a-5df6-41d5-8f0c-09ab1e8e2b33",
--   "eventType": "TenantIamProvisionedEvent",
--   "eventVersion": "1",
--   "source": "iam-provisioning-service",
--   "boundedContext": "IdentityAccess",
--   "correlationId": "corr-789",
--   "causationId": "tenant-infra-event-001",
--   "tenantId": "tenant-acme",
--   "contentType": "application/json",
--   "schemaName": "cdp.identity.TenantIamProvisionedEvent",
--   "schemaVersion": "1"
-- }
