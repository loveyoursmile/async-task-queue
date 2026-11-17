package com.example.asyncqueue.persistence.entity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务结果表实体。
 *
 * @author async-queue
 */
public class TaskResultEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String result;
    private Long costMs;
    private Long createdAt;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getCostMs() {
        return costMs;
    }

    public void setCostMs(Long costMs) {
        this.costMs = costMs;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
