package com.example.asyncqueue.config;

import com.example.asyncqueue.queue.BoundedPriorityTaskQueue;
import com.example.asyncqueue.queue.TaskMetrics;
import com.example.asyncqueue.queue.TaskQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 队列、线程池与指标的 Bean 配置。
 *
 * <p>线程池使用 ThreadPoolTaskExecutor 显式构造（禁止使用 Executors 工厂，
 * 避免无界队列导致 OOM），线程名带前缀便于问题排查。</p>
 *
 * @author async-queue
 */
@Configuration
public class TaskExecutorConfig {

    private static final String THREAD_NAME_PREFIX = "async-task-worker-";

    /**
     * 任务队列：容量 = 核心线程数 × 容量系数。
     *
     * @param properties 队列配置
     * @return 有界优先级队列
     */
    @Bean
    public TaskQueue taskQueue(QueueProperties properties) {
        return new BoundedPriorityTaskQueue(properties.capacity());
    }

    /**
     * 工作线程池：核心 = 最大，队列不使用 JDK 自带队列（由自研队列承担缓冲）。
     *
     * @param properties 队列配置
     * @return 线程池执行器
     */
    @Bean("taskExecutor")
    public Executor taskExecutor(QueueProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        // 工作线程数量固定，不依赖 JDK 队列做缓冲；自研队列在 Worker 循环中取任务
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds((int) properties.getKeepAliveSeconds());
        executor.setThreadFactory(namedThreadFactory());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 具名线程工厂，便于日志排查与 jstack 定位。
     *
     * @return 线程工厂
     */
    private ThreadFactory namedThreadFactory() {
        AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    /** @return 单例指标收集器 */
    @Bean
    public TaskMetrics taskMetrics() {
        return new TaskMetrics();
    }

    /** @return 编程式事务模板，用于精确控制持久化边界 */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
