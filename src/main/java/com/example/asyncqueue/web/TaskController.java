package com.example.asyncqueue.web;

import com.example.asyncqueue.common.Result;
import com.example.asyncqueue.common.exception.BizException;
import com.example.asyncqueue.domain.MetricsVo;
import com.example.asyncqueue.domain.SubmitRequest;
import com.example.asyncqueue.domain.TaskQueryVo;
import com.example.asyncqueue.service.TaskQueryService;
import com.example.asyncqueue.service.TaskShutdownService;
import com.example.asyncqueue.service.TaskSubmitService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 任务 REST 接口。
 *
 * @author async-queue
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskSubmitService submitService;
    private final TaskQueryService queryService;
    private final TaskShutdownService shutdownService;

    public TaskController(TaskSubmitService submitService, TaskQueryService queryService,
                          TaskShutdownService shutdownService) {
        this.submitService = submitService;
        this.queryService = queryService;
        this.shutdownService = shutdownService;
    }

    /**
     * 提交任务，快速返回任务 ID。
     *
     * @param request 提交请求
     * @return 任务 ID
     */
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody SubmitRequest request) {
        if (shutdownService.isShuttingDown()) {
            throw new BizException("SHUTTING_DOWN", "系统正在停机，拒绝新任务");
        }
        Long taskId = submitService.submit(request);
        return Result.ok(taskId);
    }

    /**
     * 按任务 ID 查询状态与结果。
     *
     * @param taskId 任务 ID
     * @return 查询结果
     */
    @GetMapping("/{taskId}")
    public Result<TaskQueryVo> query(@PathVariable Long taskId) {
        return Result.ok(queryService.query(taskId));
    }

    /**
     * 按状态统计数量（积压、处理中、失败等）。
     *
     * @return 状态数量映射
     */
    @GetMapping("/stats")
    public Result<Map<String, Integer>> stats() {
        return Result.ok(queryService.countByStatus());
    }

    /**
     * 可观测性指标。
     *
     * @return 运行指标
     */
    @GetMapping("/metrics")
    public Result<MetricsVo> metrics() {
        return Result.ok(queryService.metrics());
    }
}
