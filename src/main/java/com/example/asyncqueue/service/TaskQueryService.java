package com.example.asyncqueue.service;

import com.example.asyncqueue.domain.MetricsVo;
import com.example.asyncqueue.domain.TaskQueryVo;

/**
 * 任务查询与统计服务接口。
 *
 * @author async-queue
 */
public interface TaskQueryService {

    /**
     * 按任务 ID 查询状态与结果。
     *
     * @param taskId 任务 ID
     * @return 查询结果
     */
    TaskQueryVo query(Long taskId);

    /**
     * 按状态统计数量。
     *
     * @return 状态 → 数量
     */
    java.util.Map<String, Integer> countByStatus();

    /** @return 可观测性指标 */
    MetricsVo metrics();
}
