package com.example.asyncqueue.integration;

import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.domain.SubmitRequest;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.queue.TaskQueue;
import com.example.asyncqueue.service.TaskShutdownService;
import com.example.asyncqueue.service.TaskSubmitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优雅停机与任务不丢失集成测试。
 *
 * <p>验证核心约束：停机过程中，任务要么已执行成功，要么保持 WAITING 供重启恢复，
 * 绝不会凭空消失；同时内存队列被清空并回写数据库。</p>
 *
 * @author async-queue
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class ShutdownRecoveryIntegrationTest {

    @Autowired
    private TaskSubmitService submitService;

    @Autowired
    private TaskShutdownService shutdownService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskQueue taskQueue;

    @Test
    @DisplayName("优雅停机后任务不丢失：要么成功，要么保持 WAITING 待恢复")
    void shutdownShouldNotLoseTasks() {
        String key = "itest-shutdown-" + new Random().nextInt(Integer.MAX_VALUE);
        Long id = submitService.submit(buildRequest(key));

        // 执行优雅停机：停止取新任务 → drain 队列回写 → 线程池限时收敛
        shutdownService.shutdown();

        assertTrue(shutdownService.isShuttingDown());
        assertEquals(0, taskQueue.size(), "停机后内存队列应被清空");

        TaskEntity task = taskMapper.selectById(id);
        assertNotNull(task, "任务记录必须存在（不丢失）");
        String status = task.getStatus();
        assertTrue(TaskStatusEnum.SUCCESS.name().equals(status)
                        || TaskStatusEnum.WAITING.name().equals(status),
                "停机后任务应为成功或等待恢复，实际为：" + status);
    }

    /**
     * 构造提交请求。
     *
     * @param key 业务键
     * @return 提交请求
     */
    private SubmitRequest buildRequest(String key) {
        SubmitRequest request = new SubmitRequest();
        request.setBusinessKey(key);
        request.setTaskType("RULE_CHECK");
        request.setPayload("shutdown test");
        request.setPriority(5);
        return request;
    }
}
