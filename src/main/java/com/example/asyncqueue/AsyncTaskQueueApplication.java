package com.example.asyncqueue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步任务队列启动类。
 *
 * @author async-queue
 */
@EnableScheduling
@MapperScan("com.example.asyncqueue.persistence.mapper")
@SpringBootApplication
public class AsyncTaskQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncTaskQueueApplication.class, args);
    }
}
