package com.example.asyncqueue.common.exception;

/**
 * 队列已满时抛出，触发明确的背压拒绝（HTTP 429）。
 *
 * @author async-queue
 */
public class QueueFullException extends BizException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "QUEUE_FULL";

    public QueueFullException(int capacity) {
        super(CODE, "任务队列已满（容量 " + capacity + "），请稍后重试");
    }
}
