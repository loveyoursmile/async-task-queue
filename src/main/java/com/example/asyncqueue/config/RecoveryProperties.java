package com.example.asyncqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 任务恢复扫描配置属性。
 *
 * @author async-queue
 */
@Component
@ConfigurationProperties(prefix = "recovery")
public class RecoveryProperties {

    /** 恢复扫描周期（毫秒） */
    private long scanIntervalMs = 1000L;

    /** RUNNING 状态超时判定（毫秒） */
    private long runningTimeoutMs = 60000L;

    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }

    public long getRunningTimeoutMs() {
        return runningTimeoutMs;
    }

    public void setRunningTimeoutMs(long runningTimeoutMs) {
        this.runningTimeoutMs = runningTimeoutMs;
    }
}
