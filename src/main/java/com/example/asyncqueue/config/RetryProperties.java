package com.example.asyncqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 失败重试配置属性。
 *
 * @author async-queue
 */
@Component
@ConfigurationProperties(prefix = "retry")
public class RetryProperties {

    /** 重试次数上限（不含首次执行） */
    private int maxAttempts = 3;

    /** 指数退避基数（毫秒） */
    private long baseDelayMs = 200L;

    /** 退避上限（毫秒） */
    private long maxDelayMs = 5000L;

    /**
     * 计算第 retryCount 次重试的退避时间：base × 2^(retryCount-1)，不超过上限。
     *
     * @param retryCount 已重试次数（从 1 开始）
     * @return 退避毫秒数
     */
    public long backoffMillis(int retryCount) {
        int exponent = Math.max(0, retryCount - 1);
        long multiplier = 1L << Math.min(exponent, 30);
        long delay = baseDelayMs * multiplier;
        return Math.min(delay, maxDelayMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }
}
