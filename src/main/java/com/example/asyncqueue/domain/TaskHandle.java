package com.example.asyncqueue.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 队列中的任务句柄：只携带调度所需的最小字段，避免队列与数据库实体强耦合。
 *
 * @author async-queue
 */
public class TaskHandle implements Serializable, Comparable<TaskHandle> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long taskId;
    private final Integer priority;
    private final Long createdAt;
    private final long sequence;

    public TaskHandle(Long taskId, Integer priority, Long createdAt, long sequence) {
        this.taskId = taskId;
        this.priority = priority;
        this.createdAt = createdAt;
        this.sequence = sequence;
    }

    /**
     * 优先级比较：数值越大优先级越高；同优先级按入队序号 FIFO。
     *
     * @param other 另一个句柄
     * @return 比较结果
     */
    @Override
    public int compareTo(TaskHandle other) {
        int byPriority = Integer.compare(other.priority, this.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(this.sequence, other.sequence);
    }

    public Long getTaskId() {
        return taskId;
    }

    public Integer getPriority() {
        return priority;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public long getSequence() {
        return sequence;
    }
}
