package com.example.asyncqueue.service.impl;

import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.common.exception.BizException;
import com.example.asyncqueue.config.QueueProperties;
import com.example.asyncqueue.domain.MetricsVo;
import com.example.asyncqueue.domain.TaskQueryVo;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.entity.TaskResultEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.persistence.mapper.TaskResultMapper;
import com.example.asyncqueue.queue.TaskMetrics;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.TaskQueryService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 任务查询与统计服务实现。
 *
 * @author async-queue
 */
@Service
public class TaskQueryServiceImpl implements TaskQueryService {

    private final TaskMapper taskMapper;
    private final TaskResultMapper taskResultMapper;
    private final TaskQueue taskQueue;
    private final TaskMetrics metrics;
    private final QueueProperties queueProperties;
    private final Executor taskExecutor;

    public TaskQueryServiceImpl(TaskMapper taskMapper, TaskResultMapper taskResultMapper,
                                TaskQueue taskQueue, TaskMetrics metrics,
                                QueueProperties queueProperties,
                                @Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskMapper = taskMapper;
        this.taskResultMapper = taskResultMapper;
        this.taskQueue = taskQueue;
        this.metrics = metrics;
        this.queueProperties = queueProperties;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public TaskQueryVo query(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("TASK_NOT_FOUND", "任务不存在：" + taskId);
        }
        TaskQueryVo vo = new TaskQueryVo();
        vo.setTaskId(task.getId());
        vo.setBusinessKey(task.getBusinessKey());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        TaskStatusEnum status = TaskStatusEnum.of(task.getStatus());
        vo.setStatusDescription(status == null ? task.getStatus() : status.getDescription());
        vo.setPriority(task.getPriority());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxAttempts(task.getMaxAttempts());
        vo.setFailReason(task.getFailReason());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        if (task.getStartedAt() != null) {
            vo.setQueueTimeMs(Math.max(0L, task.getStartedAt() - task.getCreatedAt()));
        }

        TaskResultEntity result = taskResultMapper.selectByTaskId(taskId);
        if (result != null) {
            vo.setResult(result.getResult());
            vo.setCostMs(result.getCostMs());
        }
        return vo;
    }

    @Override
    public Map<String, Integer> countByStatus() {
        Map<String, Integer> counts = new LinkedHashMap<>(16);
        for (TaskStatusEnum status : TaskStatusEnum.values()) {
            counts.put(status.name(), 0);
        }
        List<TaskMapper.StatusCount> rows = taskMapper.countGroupByStatus();
        for (TaskMapper.StatusCount row : rows) {
            if (row.getStatus() != null && row.getCount() != null) {
                counts.put(row.getStatus(), row.getCount());
            }
        }
        return counts;
    }

    @Override
    public MetricsVo metrics() {
        MetricsVo vo = new MetricsVo();
        vo.setQueueSize(taskQueue.size());
        vo.setQueueCapacity(taskQueue.capacity());
        if (taskExecutor instanceof ThreadPoolTaskExecutor executor) {
            vo.setActiveThreads(executor.getActiveCount());
            vo.setPoolSize(executor.getPoolSize());
        }
        vo.setSuccessCount(metrics.getSuccessCount());
        vo.setFailedCount(metrics.getFailedCount());
        vo.setRetryCount(metrics.getRetryCount());
        vo.setDeadLetterCount(metrics.getDeadLetterCount());
        vo.setAvgQueueTimeMs(round2(metrics.avgQueueTimeMs()));
        vo.setAvgProcessTimeMs(round2(metrics.avgProcessTimeMs()));
        vo.setStatusCounts(countByStatus());
        return vo;
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 四舍五入后的值
     */
    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
