package com.example.asyncqueue.queue;

import com.example.asyncqueue.domain.TaskHandle;

/**
 * 自研有界任务队列接口。
 *
 * @author async-queue
 */
public interface TaskQueue {

    /**
     * 入队，队列满时最多等待 timeout 毫秒，超时返回 false（触发背压拒绝）。
     *
     * @param handle  任务句柄
     * @param timeout 等待超时（毫秒）
     * @return 是否成功入队
     * @throws InterruptedException 等待过程被中断
     */
    boolean offer(TaskHandle handle, long timeout) throws InterruptedException;

    /**
     * 出队，队列空时阻塞等待，超时返回 null。
     *
     * @param timeout 等待超时（毫秒）
     * @return 任务句柄，超时为 null
     * @throws InterruptedException 等待过程被中断
     */
    TaskHandle poll(long timeout) throws InterruptedException;

    /**
     * 将队列中的任务全部安全取出，用于优雅停机时回写数据库。
     *
     * @return 取出的任务句柄列表
     */
    java.util.List<TaskHandle> drainAll();

    /** @return 当前队列长度 */
    int size();

    /** @return 队列容量 */
    int capacity();

    /**
     * 判断句柄是否仍在队列中（供恢复扫描避免重复入队）。
     *
     * @param taskId 任务 ID
     * @return 是否在队列中
     */
    boolean contains(Long taskId);
}
