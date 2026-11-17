-- 任务主表：负责状态流转与幂等约束
CREATE TABLE IF NOT EXISTS task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    business_key    VARCHAR(128) NOT NULL COMMENT '业务唯一键，用于幂等',
    task_type       VARCHAR(64)  NOT NULL COMMENT '任务类型',
    payload         VARCHAR(1000)         COMMENT '任务入参 JSON',
    priority        INT          NOT NULL DEFAULT 5 COMMENT '优先级，数值越大越高',
    status          VARCHAR(20)  NOT NULL COMMENT '任务状态',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_attempts    INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_at   BIGINT                COMMENT '下次可重试时间戳(ms)，退避使用',
    fail_reason     VARCHAR(500)          COMMENT '最后一次失败原因',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      BIGINT       NOT NULL COMMENT '创建时间戳(ms)',
    updated_at      BIGINT       NOT NULL COMMENT '最后更新时间戳(ms)',
    started_at      BIGINT                COMMENT '首次开始执行时间戳(ms)',
    finished_at     BIGINT                COMMENT '完成时间戳(ms)',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_business_key UNIQUE (business_key)
);

CREATE INDEX IF NOT EXISTS idx_task_status ON task (status);
CREATE INDEX IF NOT EXISTS idx_task_retry ON task (status, next_retry_at);

-- 任务结果表：与主表分离，便于清理与独立查询
CREATE TABLE IF NOT EXISTS task_result (
    task_id      BIGINT       NOT NULL,
    result       VARCHAR(2000)         COMMENT '执行结果或失败摘要',
    cost_ms      BIGINT       NOT NULL DEFAULT 0 COMMENT '处理耗时(ms)',
    created_at   BIGINT       NOT NULL,
    PRIMARY KEY (task_id)
);
