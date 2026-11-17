package com.example.asyncqueue.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可观测性指标 VO。
 *
 * @author async-queue
 */
public class MetricsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int queueSize;
    private int queueCapacity;
    private int activeThreads;
    private int poolSize;
    private long successCount;
    private long failedCount;
    private long retryCount;
    private long deadLetterCount;
    private double avgQueueTimeMs;
    private double avgProcessTimeMs;
    private Map<String, Integer> statusCounts = new LinkedHashMap<>(8);

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getActiveThreads() {
        return activeThreads;
    }

    public void setActiveThreads(int activeThreads) {
        this.activeThreads = activeThreads;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }

    public long getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(long retryCount) {
        this.retryCount = retryCount;
    }

    public long getDeadLetterCount() {
        return deadLetterCount;
    }

    public void setDeadLetterCount(long deadLetterCount) {
        this.deadLetterCount = deadLetterCount;
    }

    public double getAvgQueueTimeMs() {
        return avgQueueTimeMs;
    }

    public void setAvgQueueTimeMs(double avgQueueTimeMs) {
        this.avgQueueTimeMs = avgQueueTimeMs;
    }

    public double getAvgProcessTimeMs() {
        return avgProcessTimeMs;
    }

    public void setAvgProcessTimeMs(double avgProcessTimeMs) {
        this.avgProcessTimeMs = avgProcessTimeMs;
    }

    public Map<String, Integer> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<String, Integer> statusCounts) {
        this.statusCounts = statusCounts;
    }
}
