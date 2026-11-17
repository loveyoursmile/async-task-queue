package com.example.asyncqueue.service;

import com.example.asyncqueue.domain.SubmitRequest;

/**
 * 任务提交服务接口。
 *
 * @author async-queue
 */
public interface TaskSubmitService {

    /**
     * 提交任务：入参校验 → 幂等判断 → 持久化 → 入队。
     *
     * @param request 提交请求
     * @return 任务 ID
     */
    Long submit(SubmitRequest request);
}
