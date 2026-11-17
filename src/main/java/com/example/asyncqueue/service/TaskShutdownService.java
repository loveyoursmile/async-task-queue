package com.example.asyncqueue.service;

/**
 * 优雅停机服务接口。
 *
 * @author async-queue
 */
public interface TaskShutdownService {

    /** 执行优雅停机流程 */
    void shutdown();

    /** @return 是否正在停机（停机期间拒绝新任务提交） */
    boolean isShuttingDown();
}
