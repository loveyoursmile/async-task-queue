package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.common.enums.FailureCategoryEnum;
import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.common.enums.TaskTypeEnum;
import com.example.asyncqueue.common.exception.TaskExecuteException;
import com.example.asyncqueue.config.RetryProperties;
import com.example.asyncqueue.domain.TaskHandle;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.entity.TaskResultEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.persistence.mapper.TaskResultMapper;
import com.example.asyncqueue.queue.TaskMetrics;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.TaskWorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 任务处理服务实现。
 *
 * <p>执行流程：从队列取出句柄 → CAS 领取（乐观锁，防重复消费）→ 模拟耗时执行 →
 * 按失败分类处理（重试 / 死信）→ 写结果表 → 更新状态。</p>
 *
 * <p>失败分类：任务自身业务异常（可重试）、系统可恢复异常（可重试）、
 * 系统不可恢复异常（直接死信）。重试通过 next_retry_at + 退避，由
 * RetryScheduler 延迟重新入队，不直接挤占内存队列。</p>
 *
 * @author async-queue
 */
@Slf4j
@Service
public class TaskWorkerServiceImpl implements TaskWorkerService {

    private static final long POLL_TIMEOUT_MS = 500L;
    private static final String FAIL_MARKER = "FORCE_FAIL";
    private static final String FATAL_MARKER = "FATAL_ERROR";

    private final TaskMapper taskMapper;
    private final TaskResultMapper taskResultMapper;
    private final TaskQueue taskQueue;
    private final TaskMetrics metrics;
    private final RetryProperties retryProperties;
    private final TransactionTemplate transactionTemplate;
    private final Executor taskExecutor;

    /** 工作线程运行标志，优雅停机时置 false */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TaskWorkerServiceImpl(TaskMapper taskMapper, TaskResultMapper taskResultMapper,
                                 TaskQueue taskQueue, TaskMetrics metrics,
                                 RetryProperties retryProperties,
                                 TransactionTemplate transactionTemplate,
                                 @Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskMapper = taskMapper;
        this.taskResultMapper = taskResultMapper;
        this.taskQueue = taskQueue;
        this.metrics = metrics;
        this.retryProperties = retryProperties;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void startWorkers(int workerCount) {
        if (!running.compareAndSet(false, true)) {
            log.warn("工作线程已启动，忽略重复启动请求");
            return;
        }
        for (int i = 0; i < workerCount; i++) {
            taskExecutor.execute(this::workLoop);
        }
        log.info("已启动 {} 个工作线程", workerCount);
    }

    @Override
    public void stopWorkers() {
        running.set(false);
        log.info("已发出工作线程停止信号");
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 工作线程主循环：持续从队列取任务处理，直到收到停止信号。
     */
    private void workLoop() {
        while (running.get()) {
            try {
                TaskHandle handle = taskQueue.poll(POLL_TIMEOUT_MS);
                if (handle == null) {
                    continue;
                }
                processSafely(handle);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.info("工作线程被中断，退出循环");
                break;
            } catch (Exception ex) {
                log.error("工作循环异常", ex);
            }
        }
        log.info("工作线程退出：{}", Thread.currentThread().getName());
    }

    /**
     * 安全处理单个任务：任何异常都不允许冲垮工作线程。
     *
     * @param handle 任务句柄
     */
    private void processSafely(TaskHandle handle) {
        try {
            process(handle);
        } catch (Exception ex) {
            log.error("任务处理出现未预期异常 taskId={}", handle.getTaskId(), ex);
        }
    }

    @Override
    public void process(TaskHandle handle) {
        long startTime = System.currentTimeMillis();
        TaskEntity task = taskMapper.selectById(handle.getTaskId());
        if (task == null) {
            log.warn("任务句柄对应的数据库记录不存在 taskId={}", handle.getTaskId());
            return;
        }

        // CAS 领取：只有 WAITING 且版本匹配才能领取，防止恢复扫描导致的重复执行
        int claimed = taskMapper.claimTask(task.getId(), task.getVersion(), startTime);
        if (claimed != 1) {
            log.debug("任务已被其他线程领取或状态已变更 taskId={}, status={}",
                    task.getId(), task.getStatus());
            return;
        }
        task.setVersion(task.getVersion() + 1);
        task.setStatus(TaskStatusEnum.RUNNING.name());
        task.setStartedAt(task.getStartedAt() == null ? startTime : task.getStartedAt());

        long queueTimeMs = Math.max(0L, startTime - task.getCreatedAt());
        long processTimeMs;
        try {
            String result = simulateExecute(task);
            processTimeMs = System.currentTimeMillis() - startTime;
            onSuccess(task, result, processTimeMs, queueTimeMs);
        } catch (TaskExecuteException ex) {
            processTimeMs = System.currentTimeMillis() - startTime;
            onBusinessFailure(task, ex, processTimeMs);
        } catch (Exception ex) {
            processTimeMs = System.currentTimeMillis() - startTime;
            // 未分类异常按「系统可恢复」处理，纳入重试治理
            onSystemFailure(task, FailureCategoryEnum.SYSTEM_RECOVERABLE,
                    ex.getMessage(), processTimeMs);
        }
    }

    /**
     * 处理成功：写结果表 + 状态置 SUCCESS（同一事务）。
     */
    private void onSuccess(TaskEntity task, String result, long processTimeMs, long queueTimeMs) {
        long finishedAt = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> {
            TaskResultEntity resultEntity = new TaskResultEntity();
            resultEntity.setTaskId(task.getId());
            resultEntity.setResult(result);
            resultEntity.setCostMs(processTimeMs);
            resultEntity.setCreatedAt(finishedAt);
            taskResultMapper.insert(resultEntity);
            taskMapper.markSuccess(task.getId(), task.getVersion(), finishedAt);
        });
        metrics.recordSuccess(queueTimeMs, processTimeMs);
        log.info("任务执行成功 taskId={}, costMs={}", task.getId(), processTimeMs);
    }

    /**
     * 处理业务异常：按分类决定是否重试。
     */
    private void onBusinessFailure(TaskEntity task, TaskExecuteException ex, long processTimeMs) {
        onSystemFailure(task, ex.getCategory(), ex.getMessage(), processTimeMs);
    }

    /**
     * 统一处理失败：不可恢复或超过重试上限进死信；否则计算退避等待重试。
     */
    private void onSystemFailure(TaskEntity task, FailureCategoryEnum category,
                                 String reason, long processTimeMs) {
        metrics.recordFailed();
        metrics.recordProcessTime(processTimeMs);
        boolean retryable = category.isRetryable() && task.getRetryCount() < task.getMaxAttempts();
        if (!retryable) {
            long finishedAt = System.currentTimeMillis();
            String failReason = category.name() + ": " + truncate(reason, 480);
            transactionTemplate.executeWithoutResult(
                    status -> taskMapper.markDeadLetter(task.getId(), task.getVersion(),
                            failReason, finishedAt));
            metrics.recordDeadLetter();
            log.warn("任务进入死信 taskId={}, category={}, reason={}",
                    task.getId(), category, failReason);
            return;
        }

        int nextRetryCount = task.getRetryCount() + 1;
        long nextRetryAt = System.currentTimeMillis() + retryProperties.backoffMillis(nextRetryCount);
        transactionTemplate.executeWithoutResult(status -> taskMapper.markRetryWaiting(
                task.getId(), task.getVersion(), nextRetryCount, nextRetryAt,
                truncate(category.name() + ": " + reason, 480)));
        metrics.recordRetry();
        log.info("任务失败待重试 taskId={}, retryCount={}, nextRetryAt={}, category={}",
                task.getId(), nextRetryCount, nextRetryAt, category);
    }

    /**
     * 模拟任务执行：按任务类型耗时区间 sleep，按失败概率或强制标记模拟失败。
     *
     * @param task 任务实体
     * @return 模拟结果
     * @throws TaskExecuteException 模拟失败
     */
    private String simulateExecute(TaskEntity task) throws TaskExecuteException {
        TaskTypeEnum type = TaskTypeEnum.of(task.getTaskType());
        if (type == null) {
            throw TaskExecuteException.fatal("未知任务类型：" + task.getTaskType());
        }
        long min = type.getMinCostMs();
        long max = type.getMaxCostMs();
        long cost = min + ThreadLocalRandom.current().nextLong(max - min + 1L);
        try {
            Thread.sleep(cost);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw TaskExecuteException.recoverable("执行被中断");
        }

        String payload = Objects.toString(task.getPayload(), "");
        if (payload.contains(FATAL_MARKER)) {
            throw TaskExecuteException.fatal("模拟系统不可恢复异常");
        }
        if (payload.contains(FAIL_MARKER)) {
            throw TaskExecuteException.business("模拟业务失败");
        }
        int failPercent = type.getDefaultFailPercent();
        if (failPercent > 0 && ThreadLocalRandom.current().nextInt(100) < failPercent) {
            throw TaskExecuteException.business("模拟任务自身失败（随机命中失败概率）");
        }
        return "{\"taskId\":" + task.getId() + ",\"summary\":\"模拟摘要结果\",\"costMs\":" + cost + "}";
    }

    /**
     * 按最大长度截断字符串，避免超出数据库字段长度。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
