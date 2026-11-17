package com.example.asyncqueue.integration;

import com.example.asyncqueue.common.enums.TaskStatusEnum;
import com.example.asyncqueue.domain.SubmitRequest;
import com.example.asyncqueue.persistence.entity.TaskEntity;
import com.example.asyncqueue.persistence.mapper.TaskMapper;
import com.example.asyncqueue.service.TaskSubmitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 任务提交与幂等集成测试（使用内存 H2，通过测试配置覆盖）。
 *
 * @author async-queue
 */
@SpringBootTest
class TaskSubmitIntegrationTest {

    @Autowired
    private TaskSubmitService submitService;

    @Autowired
    private TaskMapper taskMapper;

    @Test
    @DisplayName("重复提交同一业务键返回同一任务 ID")
    void duplicateSubmitShouldReturnSameId() {
        String key = "itest-dedup-" + new Random().nextInt(Integer.MAX_VALUE);
        SubmitRequest request = buildRequest(key, "RULE_CHECK", "norm", 5);
        Long firstId = submitService.submit(request);
        Long secondId = submitService.submit(request);
        assertEquals(firstId, secondId, "幂等：重复提交应返回相同任务 ID");
    }

    @Test
    @DisplayName("提交后任务以 WAITING 状态落库，业务键唯一")
    void submitShouldPersistWaitingTask() {
        String key = "itest-persist-" + new Random().nextInt(Integer.MAX_VALUE);
        Long id = submitService.submit(buildRequest(key, "RULE_CHECK", "norm", 5));
        TaskEntity entity = taskMapper.selectById(id);
        assertNotNull(entity);
        assertEquals(key, entity.getBusinessKey());
        assertEquals(TaskStatusEnum.WAITING.name(), entity.getStatus());
    }

    /**
     * 构造提交请求。
     *
     * @param key      业务键
     * @param type     任务类型
     * @param payload  入参
     * @param priority 优先级
     * @return 提交请求
     */
    private SubmitRequest buildRequest(String key, String type, String payload, int priority) {
        SubmitRequest request = new SubmitRequest();
        request.setBusinessKey(key);
        request.setTaskType(type);
        request.setPayload(payload);
        request.setPriority(priority);
        return request;
    }
}
