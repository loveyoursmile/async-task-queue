package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.common.enums.TaskTypeEnum;
import com.example.asyncqueue.common.exception.BizException;
import com.example.asyncqueue.common.exception.QueueFullException;
import com.example.asyncqueue.config.QueueProperties;
import com.example.asyncqueue.config.RetryProperties;
import com.example.asyncqueue.domain.SubmitRequest;
import com.example.asyncqueue.domain.TaskHandle;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.TaskSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务提交服务实现。
 *
 * <p>持久化边界：「接收成功」以数据库事务提交为准——记录以 WAITING 状态落库，
 * 唯一键保证幂等；随后入内存队列。持久化使用编程式事务，事务在入队前已提交，
 * 因此入队失败（背压拒绝）时任务仍留在数据库中，由恢复扫描兜底重新入队，
 * 保证「任务一旦接收就不丢失」。</p>
 *
 * @author async-queue
 */
@Slf4j
@Service
public class TaskSubmitServiceImpl implements TaskSubmitService {

    /** 入队序号生成器，用于同优先级任务的 FIFO 排序 */
    private final AtomicLong sequenceGenerator = new AtomicLong(0L);

    private final TaskMapper taskMapper;
    private final TaskQueue taskQueue;
    private final QueueProperties queueProperties;
    private final RetryProperties retryProperties;
    private final TransactionTemplate transactionTemplate;

    public TaskSubmitServiceImpl(TaskMapper taskMapper, TaskQueue taskQueue,
                                 QueueProperties queueProperties, RetryProperties retryProperties,
                                 TransactionTemplate transactionTemplate) {
        this.taskMapper = taskMapper;
        this.taskQueue = taskQueue;
        this.queueProperties = queueProperties;
        this.retryProperties = retryProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Long submit(SubmitRequest request) {
        TaskTypeEnum taskType = TaskTypeEnum.of(request.getTaskType());
        if (taskType == null) {
            throw new BizException("INVALID_TASK_TYPE", "不支持的任务类型：" + request.getTaskType());
        }

        // 1. 持久化到数据库（独立事务提交），这一步成功即「接收成功」
        TaskEntity task = transactionTemplate.execute(status -> {
            TaskEntity existing = taskMapper.selectByBusinessKey(request.getBusinessKey());
            if (existing != null) {
                log.info("重复提交，返回已有任务 taskId={}, businessKey={}",
                        existing.getId(), request.getBusinessKey());
                return existing;
            }
            TaskEntity entity = buildTask(request, taskType);
            insertWithDedup(entity);
            return entity;
        });

        // 2. 入队：内存队列满时触发背压拒绝；任务已落库，不丢失
        TaskHandle handle = new TaskHandle(task.getId(), task.getPriority(), task.getCreatedAt(),
                sequenceGenerator.getAndIncrement());
        boolean offered;
        try {
            offered = taskQueue.offer(handle, queueProperties.getOfferTimeoutMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException("SUBMIT_INTERRUPTED", "提交被中断，任务已持久化将由恢复扫描入队");
        }
        if (!offered) {
            // 任务已落在数据库，由恢复扫描兜底重新入队，保证不丢失
            log.warn("队列已满，任务已持久化等待恢复入队 taskId={}", task.getId());
            throw new QueueFullException(taskQueue.capacity());
        }
        log.info("任务提交成功 taskId={}, type={}, priority={}",
                task.getId(), taskType.name(), task.getPriority());
        return task.getId();
    }

    /**
     * 插入任务，并发下捕获唯一键冲突后按业务键回查，保证幂等。
     *
     * @param task 待插入任务
     */
    private void insertWithDedup(TaskEntity task) {
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            TaskEntity concurrent = taskMapper.selectByBusinessKey(task.getBusinessKey());
            if (concurrent != null) {
                log.info("并发重复提交，复用已有任务 taskId={}", concurrent.getId());
                task.setId(concurrent.getId());
                return;
            }
            throw ex;
        }
    }

    /**
     * 构造任务实体。
     *
     * @param request  提交请求
     * @param taskType 任务类型
     * @return 任务实体
     */
    private TaskEntity buildTask(SubmitRequest request, TaskTypeEnum taskType) {
        TaskEntity task = new TaskEntity();
        long now = System.currentTimeMillis();
        task.setBusinessKey(request.getBusinessKey());
        task.setTaskType(taskType.name());
        task.setPayload(Objects.toString(request.getPayload(), ""));
        task.setPriority(request.getPriority() == null ? 5 : request.getPriority());
        task.setStatus(TaskStatusEnum.WAITING.name());
        task.setRetryCount(0);
        task.setMaxAttempts(retryProperties.getMaxAttempts());
        task.setVersion(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
