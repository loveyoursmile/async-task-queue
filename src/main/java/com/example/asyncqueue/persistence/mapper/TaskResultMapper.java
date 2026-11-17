package com.example.asyncqueue.persistence.mapper;

import com.example.asyncqueue.persistence.entity.TaskResultEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 任务结果表 Mapper。
 *
 * @author async-queue
 */
public interface TaskResultMapper {

    /**
     * 插入或替换结果（一个任务只有一条结果记录）。
     *
     * @param result 结果实体
     * @return 影响行数
     */
    int insert(TaskResultEntity result);

    /**
     * 按任务 ID 查询结果。
     *
     * @param taskId 任务 ID
     * @return 结果实体，未找到为 null
     */
    TaskResultEntity selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 按任务 ID 删除结果（用于清理）。
     *
     * @param taskId 任务 ID
     * @return 影响行数
     */
    int deleteByTaskId(@Param("taskId") Long taskId);
}
