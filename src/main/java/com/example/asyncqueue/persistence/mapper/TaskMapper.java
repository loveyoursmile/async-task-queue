package com.example.asyncqueue.persistence.mapper;

import com.example.asyncqueue.persistence.entity.TaskEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务主表 Mapper。
 *
 * @author async-queue
 */
public interface TaskMapper {

    /**
     * 插入任务，成功后通过 useGeneratedKeys 回填主键。
     *
     * @param task 任务实体
     * @return 影响行数
     */
    int insert(TaskEntity task);

    /**
     * 按业务键查询（幂等判断）。
     *
     * @param businessKey 业务唯一键
     * @return 任务实体，未找到为 null
     */
    TaskEntity selectByBusinessKey(@Param("businessKey") String businessKey);

    /**
     * 按任务 ID 查询。
     *
     * @param taskId 任务 ID
     * @return 任务实体，未找到为 null
     */
    TaskEntity selectById(@Param("taskId") Long taskId);

    /**
     * CAS 领取任务：WAITING → RUNNING，带乐观锁版本号。
     *
     * @param taskId      任务 ID
     * @param expectedVer 期望版本号
     * @param startedAt   开始执行时间戳
     * @return 影响行数，1 表示领取成功
     */
    int claimTask(@Param("taskId") Long taskId,
                  @Param("expectedVer") int expectedVer,
                  @Param("startedAt") long startedAt);

    /**
     * 标记执行成功：RUNNING → SUCCESS，带乐观锁。
     *
     * @param taskId      任务 ID
     * @param expectedVer 期望版本号
     * @param finishedAt  完成时间戳
     * @return 影响行数
     */
    int markSuccess(@Param("taskId") Long taskId,
                    @Param("expectedVer") int expectedVer,
                    @Param("finishedAt") long finishedAt);

    /**
     * 标记重试等待：RUNNING → RETRY_WAITING，带乐观锁。
     *
     * @param taskId       任务 ID
     * @param expectedVer  期望版本号
     * @param retryCount   已重试次数
     * @param nextRetryAt  下次可重试时间戳
     * @param failReason   失败原因
     * @return 影响行数
     */
    int markRetryWaiting(@Param("taskId") Long taskId,
                         @Param("expectedVer") int expectedVer,
                         @Param("retryCount") int retryCount,
                         @Param("nextRetryAt") long nextRetryAt,
                         @Param("failReason") String failReason);

    /**
     * 标记死信：RUNNING → DEAD_LETTER，带乐观锁。
     *
     * @param taskId      任务 ID
     * @param expectedVer 期望版本号
     * @param failReason  失败原因
     * @param finishedAt  完成时间戳
     * @return 影响行数
     */
    int markDeadLetter(@Param("taskId") Long taskId,
                       @Param("expectedVer") int expectedVer,
                       @Param("failReason") String failReason,
                       @Param("finishedAt") long finishedAt);

    /**
     * 查询到期待重试的任务：RETRY_WAITING 且 next_retry_at <= now。
     *
     * @param now    当前时间戳
     * @param limit  最多返回条数
     * @return 任务实体列表
     */
    List<TaskEntity> selectDueRetries(@Param("now") long now, @Param("limit") int limit);

    /**
     * 查询僵死的执行中任务：RUNNING 且 updated_at < now - timeout。
     *
     * @param threshold 时间阈值
     * @param limit     最多返回条数
     * @return 任务实体列表
     */
    List<TaskEntity> selectStaleRunning(@Param("threshold") long threshold, @Param("limit") int limit);

    /**
     * 查询所有等待中的任务（启动时恢复用）。
     *
     * @param limit 最多返回条数
     * @return 任务实体列表
     */
    List<TaskEntity> selectWaiting(@Param("limit") int limit);

    /**
     * CAS 将 RETRY_WAITING 重置为 WAITING（重新入队前抢占，避免重复入队）。
     *
     * @param taskId      任务 ID
     * @param expectedVer 期望版本号
     * @param now         当前时间戳，用于更新 updated_at
     * @return 影响行数
     */
    int resetToWaiting(@Param("taskId") Long taskId,
                       @Param("expectedVer") int expectedVer,
                       @Param("now") long now);

    /**
     * CAS 将僵死 RUNNING 重置为 WAITING。
     *
     * @param taskId      任务 ID
     * @param expectedVer 期望版本号
     * @param now         当前时间戳，用于更新 updated_at
     * @return 影响行数
     */
    int resetStaleToWaiting(@Param("taskId") Long taskId,
                            @Param("expectedVer") int expectedVer,
                            @Param("now") long now);

    /**
     * 优雅停机：回写内存中未处理任务为 WAITING（本来就是 WAITING，这里更新 updated_at 留痕）。
     *
     * @param taskId 任务 ID
     * @param now    当前时间戳
     * @return 影响行数
     */
    int requeueOnShutdown(@Param("taskId") Long taskId, @Param("now") long now);

    /**
     * 按状态统计数量。
     *
     * @param status 任务状态
     * @return 数量
     */
    int countByStatus(@Param("status") String status);

    /**
     * 统计所有状态的数量分组。
     *
     * @return 状态与数量映射
     */
    List<StatusCount> countGroupByStatus();

    /**
     * 状态数量投影。
     */
    class StatusCount {
        private String status;
        private Integer count;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
