package com.example.asyncqueue.config;

import com.example.asyncqueue.service.TaskRecoveryService;
import com.example.asyncqueue.service.TaskShutdownService;
import com.example.asyncqueue.service.TaskWorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/**
 * 应用生命周期监听：启动时恢复任务并拉起工作线程，关闭时执行优雅停机。
 *
 * @author async-queue
 */
@Slf4j
@Configuration
public class LifecycleConfig implements ApplicationListener<ApplicationReadyEvent> {

    private final TaskRecoveryService recoveryService;
    private final TaskWorkerService workerService;
    private final TaskShutdownService shutdownService;
    private final QueueProperties queueProperties;

    public LifecycleConfig(TaskRecoveryService recoveryService, TaskWorkerService workerService,
                           TaskShutdownService shutdownService, QueueProperties queueProperties) {
        this.recoveryService = recoveryService;
        this.workerService = workerService;
        this.shutdownService = shutdownService;
        this.queueProperties = queueProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 1. 先恢复数据库里的存量任务到内存队列
        recoveryService.recoverOnStartup();
        // 2. 再启动工作线程消费
        workerService.startWorkers(queueProperties.getCorePoolSize());
        log.info("异步任务队列已就绪，队列容量={}", queueProperties.capacity());
    }

    /**
     * Spring 容器关闭时执行优雅停机（覆盖正常 shutdown、SIGTERM）。
     *
     * @param event 容器关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        log.info("收到容器关闭事件，开始优雅停机");
        shutdownService.shutdown();
    }
}
