package com.example.asyncqueue.service;

/**
 * 任务恢复服务接口：负责进程启动与运行期的任务兜底恢复。
 *
 * @author async-queue
 */
public interface TaskRecoveryService {

    /** 启动时恢复：把数据库中 WAITING / 僵死 RUNNING 任务重新入队 */
    void recoverOnStartup();

    /** 周期性恢复扫描 */
    void scanAndRecover();
}
