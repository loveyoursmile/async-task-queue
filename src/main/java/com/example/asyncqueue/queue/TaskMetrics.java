package com.example.asyncqueue.queue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时指标收集器，使用无锁原子计数，避免统计本身成为热点。
 *
 * @author async-queue
 */
public class TaskMetrics {

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong retryCount = new AtomicLong();
    private final AtomicLong deadLetterCount = new AtomicLong();

    /** 累计排队时长与样本数，用于求平均 */
    private final AtomicLong totalQueueTime = new AtomicLong();
    private final AtomicLong queueSamples = new AtomicLong();

    /** 累计处理时长与样本数，用于求平均 */
    private final AtomicLong totalProcessTime = new AtomicLong();
    private final AtomicLong processSamples = new AtomicLong();

    public void recordSuccess(long queueTimeMs, long processTimeMs) {
        successCount.incrementAndGet();
        addSample(totalQueueTime, queueSamples, queueTimeMs);
        addSample(totalProcessTime, processSamples, processTimeMs);
    }

    public void recordFailed() {
        failedCount.incrementAndGet();
    }

    public void recordRetry() {
        retryCount.incrementAndGet();
    }

    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    public void recordProcessTime(long processTimeMs) {
        addSample(totalProcessTime, processSamples, processTimeMs);
    }

    private void addSample(AtomicLong total, AtomicLong samples, long value) {
        total.addAndGet(Math.max(0L, value));
        samples.incrementAndGet();
    }

    /** @return 平均排队时长（毫秒），无样本为 0 */
    public double avgQueueTimeMs() {
        long count = queueSamples.get();
        return count == 0L ? 0.0D : totalQueueTime.get() * 1.0D / count;
    }

    /** @return 平均处理时长（毫秒），无样本为 0 */
    public double avgProcessTimeMs() {
        long count = processSamples.get();
        return count == 0L ? 0.0D : totalProcessTime.get() * 1.0D / count;
    }

    public long getSuccessCount() {
        return successCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

    public long getRetryCount() {
        return retryCount.get();
    }

    public long getDeadLetterCount() {
        return deadLetterCount.get();
    }
}
