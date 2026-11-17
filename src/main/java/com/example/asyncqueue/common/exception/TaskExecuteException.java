package com.example.asyncqueue.common.exception;

import com.example.asyncqueue.common.enums.FailureCategoryEnum;

/**
 * 任务执行失败异常，携带失败分类，供重试策略判定。
 *
 * @author async-queue
 */
public class TaskExecuteException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final FailureCategoryEnum category;

    public TaskExecuteException(FailureCategoryEnum category, String message) {
        super(message);
        this.category = category;
    }

    public FailureCategoryEnum getCategory() {
        return category;
    }

    /** 业务异常：可重试。 */
    public static TaskExecuteException business(String message) {
        return new TaskExecuteException(FailureCategoryEnum.BUSINESS, message);
    }

    /** 系统可恢复异常：可重试。 */
    public static TaskExecuteException recoverable(String message) {
        return new TaskExecuteException(FailureCategoryEnum.SYSTEM_RECOVERABLE, message);
    }

    /** 系统不可恢复异常：直接死信。 */
    public static TaskExecuteException fatal(String message) {
        return new TaskExecuteException(FailureCategoryEnum.SYSTEM_FATAL, message);
    }
}
