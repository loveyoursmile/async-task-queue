package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.config.QueueProperties;
import com.example.asyncqueue.domain.TaskHandle;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.RetryScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 延迟重试调度器实现。
 *
 * <p>重试任务不直接入内存队列，而是落库为 RETRY_WAITING + next_retry_at，
 * 由本调度器按固定周期扫描到期任务，CAS 抢占成功后重新入队。
 * 这样重试请求不会在失败瞬间放大队列压力，且进程重启后仍能恢复。</p>
 *
 * @author async-queue
 */
@Slf4j
@Service
public class RetrySchedulerImpl implements RetryScheduler {

    private static final int SCAN_LIMIT = 200;

    private final TaskMapper taskMapper;
    private final TaskQueue taskQueue;
    private final QueueProperties queueProperties;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicLong sequenceGenerator = new AtomicLong(Long.MAX_VALUE / 2);

    public RetrySchedulerImpl(TaskMapper taskMapper, TaskQueue taskQueue, QueueProperties queueProperties) {
        this.taskMapper = taskMapper;
        this.taskQueue = taskQueue;
        this.queueProperties = queueProperties;
    }

    @Override
    @Scheduled(fixedDelayString = "${recovery.scan-interval-ms:1000}")
    public void dispatchDueRetries() {
        if (!enabled.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<TaskEntity> dueTasks = taskMapper.selectDueRetries(now, SCAN_LIMIT);
        for (TaskEntity task : dueTasks) {
            // CAS 抢占：RETRY_WAITING → WAITING，避免多个节点/多次扫描重复入队
            int updated = taskMapper.resetToWaiting(task.getId(), task.getVersion(), now);
            if (updated != 1) {
                continue;
            }
            TaskHandle handle = new TaskHandle(task.getId(), task.getPriority(), task.getCreatedAt(),
                    sequenceGenerator.getAndIncrement());
            try {
                boolean offered = taskQueue.offer(handle, queueProperties.getOfferTimeoutMs());
                if (!offered) {
                    // 入队失败：状态已重置为 WAITING，由恢复扫描兜底，不丢失
                    log.warn("重试任务入队失败，等待恢复扫描 taskId={}", task.getId());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("重试入队被中断 taskId={}", task.getId());
                break;
            }
            log.debug("重试任务重新入队 taskId={}", task.getId());
        }
    }

    @Override
    public void start() {
        enabled.set(true);
    }

    @Override
    public void stop() {
        enabled.set(false);
        log.info("重试调度器已停止");
    }
}
