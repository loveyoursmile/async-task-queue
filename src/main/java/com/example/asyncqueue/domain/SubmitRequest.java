package com.example.asyncqueue.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 任务提交请求 DTO。
 *
 * @author async-queue
 */
public class SubmitRequest {

    /** 业务唯一键，用于幂等：同一 key 重复提交返回同一任务 ID */
    @NotBlank(message = "businessKey 不能为空")
    @Size(max = 128, message = "businessKey 长度不能超过 128")
    private String businessKey;

    /** 任务类型：DOCUMENT_SUMMARY / VECTOR_EMBEDDING / RULE_CHECK */
    @NotBlank(message = "taskType 不能为空")
    private String taskType;

    /** 业务参数 JSON */
    @Size(max = 1000, message = "payload 长度不能超过 1000")
    private String payload;

    /** 优先级：数值越大越高，范围 0-100 */
    @Min(value = 0, message = "priority 最小为 0")
    @Max(value = 100, message = "priority 最大为 100")
    private Integer priority = 5;

    /** 模拟强制失败，便于测试重试与死信；不传则不强制失败 */
    private Boolean forceFail;

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getForceFail() {
        return forceFail;
    }

    public void setForceFail(Boolean forceFail) {
        this.forceFail = forceFail;
    }
}
