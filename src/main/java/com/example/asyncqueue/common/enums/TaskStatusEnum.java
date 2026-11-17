package com.example.asyncqueue.common.enums;

import java.util.Arrays;
import java.util.Objects;

/**
 * 任务状态枚举。
 *
 * <p>状态流转：WAITING → RUNNING → SUCCESS / DEAD_LETTER；
 * RUNNING → RETRY_WAITING → WAITING（退避到期后重新入队）；
 * 超时 RUNNING → WAITING（恢复扫描重新入队）。</p>
 *
 * @author async-queue
 */
public enum TaskStatusEnum {

    /** 已持久化并入队，等待工作线程领取 */
    WAITING("等待中", false),
    /** 工作线程执行中 */
    RUNNING("执行中", false),
    /** 执行失败，等待退避后重试 */
    RETRY_WAITING("待重试", false),
    /** 执行成功 */
    SUCCESS("成功", true),
    /** 超过重试上限或不可恢复失败，进入死信 */
    DEAD_LETTER("死信", true);

    private final String description;
    private final boolean terminal;

    TaskStatusEnum(String description, boolean terminal) {
        this.description = description;
        this.terminal = terminal;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 根据名称解析枚举，未匹配返回 null。
     *
     * @param name 枚举名称
     * @return 任务状态，未匹配为 null
     */
    public static TaskStatusEnum of(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.name(), name))
                .findFirst()
                .orElse(null);
    }
}
