package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.config.ShutdownProperties;
import com.example.asyncqueue.domain.TaskHandle;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.RetryScheduler;
import com.example.asyncqueue.service.TaskRecoveryService;
import com.example.asyncqueue.service.TaskShutdownService;
import com.example.asyncqueue.service.TaskWorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 优雅停机服务实现。
 *
 * <p>流程：标记停机（拒绝新提交）→ 停止工作线程取新任务 → 停止重试调度与恢复扫描 →
 * 把内存队列中未处理任务回写数据库（保持 WAITING）→ 等待线程池在限时内收敛。</p>
 *
 * @author async-queue
 */
@Slf4j
@Service
public class TaskShutdownServiceImpl implements TaskShutdownService {

    private final TaskQueue taskQueue;
    private final TaskMapper taskMapper;
    private final TaskWorkerService workerService;
    private final RetryScheduler retryScheduler;
    private final TaskRecoveryServiceImpl recoveryService;
    private final ShutdownProperties shutdownProperties;
    private final Executor taskExecutor;

    private volatile boolean shuttingDown = false;

    public TaskShutdownServiceImpl(TaskQueue taskQueue, TaskMapper taskMapper,
                                   TaskWorkerService workerService, RetryScheduler retryScheduler,
                                   TaskRecoveryServiceImpl recoveryService,
                                   ShutdownProperties shutdownProperties,
                                   @Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskQueue = taskQueue;
        this.taskMapper = taskMapper;
        this.workerService = workerService;
        this.retryScheduler = retryScheduler;
        this.recoveryService = recoveryService;
        this.shutdownProperties = shutdownProperties;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        log.info("===== 优雅停机开始 =====");

        // 1. 停止接收新任务：工作线程不再从队列取任务
        workerService.stopWorkers();
        retryScheduler.stop();
        recoveryService.stop();

        // 2. 把内存队列中未处理的任务安全回写（任务本来就是 WAITING，这里留痕并确认）
        List<TaskHandle> drained = taskQueue.drainAll();
        int requeued = 0;
        for (TaskHandle handle : drained) {
            TaskEntity task = taskMapper.selectById(handle.getTaskId());
            if (task != null && com.example.asyncqueue.common.enums.TaskStatusEnum.WAITING.name()
                    .equals(task.getStatus())) {
                taskMapper.requeueOnShutdown(handle.getTaskId(), System.currentTimeMillis());
                requeued++;
            }
        }
        log.info("优雅停机：内存队列回写 {} 个任务（共取出 {} 个）", requeued, drained.size());

        // 3. 等待线程池在限时内收敛
        if (taskExecutor instanceof ThreadPoolTaskExecutor executor) {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            pool.shutdown();
            try {
                boolean converged = pool.awaitTermination(
                        shutdownProperties.getAwaitTerminationSeconds(), TimeUnit.SECONDS);
                if (!converged) {
                    List<Runnable> pending = pool.shutdownNow();
                    log.warn("线程池未在限时内收敛，强制关闭，未执行任务数={}", pending.size());
                    if (!pool.awaitTermination(5L, TimeUnit.SECONDS)) {
                        log.error("线程池强制关闭后仍未收敛");
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
                log.warn("等待线程池收敛时被中断，已强制关闭");
            }
        }
        log.info("===== 优雅停机完成 =====");
    }
}
