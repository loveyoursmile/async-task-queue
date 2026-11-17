package com.example.asyncqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 队列容量与线程池配置属性。
 *
 * <p>容量决策：默认按「核心线程数 × 容量系数」静态配置，
 * 系数可由运维按消费速率与下游耗时调整，无需改代码。</p>
 *
 * @author async-queue
 */
@Component
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    /** 核心线程数 */
    private int corePoolSize = 4;

    /** 最大线程数 */
    private int maxPoolSize = 4;

    /** 容量系数：队列容量 = 线程数 × 系数 */
    private int capacityFactor = 8;

    /** 队列满时 offer 等待超时（毫秒），超时触发背压拒绝 */
    private long offerTimeoutMs = 50L;

    /** 空闲线程存活时间（秒） */
    private long keepAliveSeconds = 60L;

    /**
     * 计算队列容量：线程数 × 容量系数。
     *
     * @return 队列容量
     */
    public int capacity() {
        return corePoolSize * capacityFactor;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getCapacityFactor() {
        return capacityFactor;
    }

    public void setCapacityFactor(int capacityFactor) {
        this.capacityFactor = capacityFactor;
    }

    public long getOfferTimeoutMs() {
        return offerTimeoutMs;
    }

    public void setOfferTimeoutMs(long offerTimeoutMs) {
        this.offerTimeoutMs = offerTimeoutMs;
    }

    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(long keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }
}
