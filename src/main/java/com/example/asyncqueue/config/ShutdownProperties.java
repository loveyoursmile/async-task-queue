package com.example.asyncqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 优雅停机配置属性。
 *
 * @author async-queue
 */
@Component
@ConfigurationProperties(prefix = "shutdown")
public class ShutdownProperties {

    /** 等待线程池收敛的最长秒数 */
    private int awaitTerminationSeconds = 10;

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }
}
