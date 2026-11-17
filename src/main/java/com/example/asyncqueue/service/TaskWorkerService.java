package com.example.asyncqueue.service;

import com.example.asyncqueue.domain.TaskHandle;

/**
 * 任务处理服务接口。
 *
 * @author async-queue
 */
public interface TaskWorkerService {

    /**
     * 处理单个任务（领取 → 执行 → 落结果 → 状态流转）。
     *
     * @param handle 任务句柄
     */
    void process(TaskHandle handle);

    /**
     * 启动指定数量的工作线程循环。
     *
     * @param workerCount 工作线程数
     */
    void startWorkers(int workerCount);

    /** 停止工作线程循环（优雅停机阶段一：停止取新任务） */
    void stopWorkers();
}
