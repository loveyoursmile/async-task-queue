package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.config.QueueProperties;
import com.example.asyncqueue.config.RecoveryProperties;
import com.example.asyncqueue.domain.TaskHandle;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.TaskRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务恢复服务实现。
 *
 * <p>数据库是唯一真相源：周期扫描 WAITING（已落库但可能不在内存队列中）与
 * 僵死 RUNNING（进程崩溃导致状态未收敛）任务，CAS 重置后重新入队，
 * 保证「任务一旦接收就不丢失」。</p>
 *
 * @author async-queue
 */
@Slf4j
@Service
public class TaskRecoveryServiceImpl implements TaskRecoveryService {

    private static final int SCAN_LIMIT = 500;

    private final TaskMapper taskMapper;
    private final TaskQueue taskQueue;
    private final QueueProperties queueProperties;
    private final RecoveryProperties recoveryProperties;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicLong sequenceGenerator = new AtomicLong(Long.MAX_VALUE / 4);

    public TaskRecoveryServiceImpl(TaskMapper taskMapper, TaskQueue taskQueue,
                                   QueueProperties queueProperties,
                                   RecoveryProperties recoveryProperties) {
        this.taskMapper = taskMapper;
        this.taskQueue = taskQueue;
        this.queueProperties = queueProperties;
        this.recoveryProperties = recoveryProperties;
    }

    @Override
    public void recoverOnStartup() {
        log.info("启动任务恢复扫描开始");
        scanAndRecover();
        log.info("启动任务恢复扫描完成，当前队列长度={}", taskQueue.size());
    }

    @Override
    @Scheduled(fixedDelayString = "${recovery.scan-interval-ms:1000}")
    public void scanAndRecover() {
        if (!enabled.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        // 1. 恢复僵死的 RUNNING 任务（进程崩溃时状态未收敛）
        long threshold = now - recoveryProperties.getRunningTimeoutMs();
        List<TaskEntity> staleRunning = taskMapper.selectStaleRunning(threshold, SCAN_LIMIT);
        for (TaskEntity task : staleRunning) {
            if (taskMapper.resetStaleToWaiting(task.getId(), task.getVersion(), now) == 1) {
                offerSafely(task);
                log.info("恢复僵死任务 taskId={}", task.getId());
            }
        }

        // 2. 恢复 WAITING 但不在内存队列中的任务（队列满拒绝入队 / 重启丢失内存）
        List<TaskEntity> waiting = taskMapper.selectWaiting(SCAN_LIMIT);
        for (TaskEntity task : waiting) {
            if (!taskQueue.contains(task.getId())) {
                offerSafely(task);
            }
        }
    }

    /**
     * 安全入队：队列满时跳过，下一轮扫描继续尝试。
     *
     * @param task 待入队任务
     */
    private void offerSafely(TaskEntity task) {
        TaskHandle handle = new TaskHandle(task.getId(), task.getPriority(), task.getCreatedAt(),
                sequenceGenerator.getAndIncrement());
        try {
            boolean offered = taskQueue.offer(handle, queueProperties.getOfferTimeoutMs());
            if (!offered) {
                log.debug("恢复入队跳过（队列满） taskId={}", task.getId());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("恢复入队被中断 taskId={}", task.getId());
        }
    }

    /** 停止恢复扫描（优雅停机阶段使用） */
    public void stop() {
        enabled.set(false);
        log.info("恢复扫描已停止");
    }
}
