package com.example.asyncqueue.common.enums;

/**
 * 失败分类枚举。
 *
 * <p>区分「任务自身失败（业务异常，可重试）」与「系统异常（可恢复 / 不可恢复）」：</p>
 * <ul>
 *     <li>BUSINESS：业务异常，按次数上限 + 退避重试；</li>
 *     <li>SYSTEM_RECOVERABLE：系统可恢复异常（如依赖抖动），按次数上限 + 退避重试；</li>
 *     <li>SYSTEM_FATAL：系统不可恢复异常（如参数非法、代码缺陷），直接死信不重试。</li>
 * </ul>
 *
 * @author async-queue
 */
public enum FailureCategoryEnum {

    /** 业务异常，可重试 */
    BUSINESS(true),
    /** 系统可恢复异常，可重试 */
    SYSTEM_RECOVERABLE(true),
    /** 系统不可恢复异常，直接死信 */
    SYSTEM_FATAL(false);

    private final boolean retryable;

    FailureCategoryEnum(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
