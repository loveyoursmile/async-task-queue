package com.example.asyncqueue.service;

/**
 * 延迟重试调度器接口。
 *
 * @author async-queue
 */
public interface RetryScheduler {

    /** 扫描到期待重试任务，CAS 抢占后重新入队 */
    void dispatchDueRetries();

    /** 启动定时调度 */
    void start();

    /** 停止定时调度 */
    void stop();
}
