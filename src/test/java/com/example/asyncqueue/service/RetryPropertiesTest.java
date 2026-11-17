package com.example.asyncqueue.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退避策略单元测试。
 *
 * @author async-queue
 */
class RetryPropertiesTest {

    private RetryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RetryProperties();
        properties.setBaseDelayMs(200L);
        properties.setMaxDelayMs(5000L);
    }

    @Test
    @DisplayName("指数退避：200、400、800 依次翻倍")
    void backoffShouldDoubleExponentially() {
        assertEquals(200L, properties.backoffMillis(1));
        assertEquals(400L, properties.backoffMillis(2));
        assertEquals(800L, properties.backoffMillis(3));
    }

    @Test
    @DisplayName("退避不超过配置上限")
    void backoffShouldNotExceedMax() {
        assertEquals(5000L, properties.backoffMillis(20));
    }
}
