package com.example.asyncqueue.queue;

import com.example.asyncqueue.domain.TaskHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有界优先级队列单元测试。
 *
 * @author async-queue
 */
class BoundedPriorityTaskQueueTest {

    private BoundedPriorityTaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new BoundedPriorityTaskQueue(3);
    }

    @Test
    @DisplayName("容量有界：满时 offer 超时返回 false，触发背压拒绝")
    void offerShouldRejectWhenFull() throws InterruptedException {
        assertTrue(queue.offer(handle(1L, 5, 0L), 10L));
        assertTrue(queue.offer(handle(2L, 5, 0L), 10L));
        assertTrue(queue.offer(handle(3L, 5, 0L), 10L));
        assertFalse(queue.offer(handle(4L, 5, 0L), 20L), "队列满时应返回 false");
        assertEquals(3, queue.size());
    }

    @Test
    @DisplayName("优先级排序：优先级高的先出队，同优先级按入队顺序 FIFO")
    void pollShouldFollowPriorityAndFifo() throws InterruptedException {
        queue.offer(handle(1L, 3, 0L), 10L);
        queue.offer(handle(2L, 9, 0L), 10L);
        queue.offer(handle(3L, 3, 0L), 10L);
        queue.offer(handle(4L, 9, 0L), 10L);

        // 容量 3，上面第 4 个会被拒绝；这里重建验证排序
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(4);
        q.offer(handle(1L, 3, 0L), 10L);
        q.offer(handle(2L, 9, 0L), 10L);
        q.offer(handle(3L, 3, 0L), 10L);
        q.offer(handle(4L, 9, 0L), 10L);

        assertEquals(2L, q.poll(10L).getTaskId());
        assertEquals(4L, q.poll(10L).getTaskId());
        assertEquals(1L, q.poll(10L).getTaskId());
        assertEquals(3L, q.poll(10L).getTaskId());
    }

    @Test
    @DisplayName("空队列 poll 超时返回 null")
    void pollShouldReturnNullOnTimeout() throws InterruptedException {
        assertNull(queue.poll(20L));
    }

    @Test
    @DisplayName("drainAll 取出全部未处理任务且清空队列")
    void drainAllShouldEmptyQueue() throws InterruptedException {
        queue.offer(handle(1L, 5, 0L), 10L);
        queue.offer(handle(2L, 5, 0L), 10L);
        List<TaskHandle> drained = queue.drainAll();
        assertEquals(2, drained.size());
        assertEquals(0, queue.size());
        assertTrue(queue.drainAll().isEmpty());
    }

    @Test
    @DisplayName("contains 反映队列中的任务，出队后为 false")
    void containsShouldReflectQueueState() throws InterruptedException {
        assertFalse(queue.contains(1L));
        queue.offer(handle(1L, 5, 0L), 10L);
        assertTrue(queue.contains(1L));
        queue.poll(10L);
        assertFalse(queue.contains(1L));
    }

    @Test
    @DisplayName("非法容量构造应抛出异常")
    void constructorShouldRejectInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedPriorityTaskQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedPriorityTaskQueue(-1));
    }

    @Test
    @DisplayName("并发生产消费不丢任务（3 生产者 + 2 消费者）")
    void concurrentProduceConsumeShouldNotLoseTasks() throws InterruptedException {
        final int perProducer = 200;
        final int producerCount = 3;
        final int total = perProducer * producerCount;
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(32);

        java.util.concurrent.atomic.AtomicInteger consumed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.Set<Long> consumedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.atomic.AtomicBoolean producersDone = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 2 个消费者：持续 poll 直到生产结束且队列清空
        List<Thread> consumers = new ArrayList<>();
        for (int c = 0; c < 2; c++) {
            Thread consumer = new Thread(() -> {
                while (!producersDone.get() || q.size() > 0) {
                    try {
                        TaskHandle handle = q.poll(50L);
                        if (handle != null) {
                            consumedIds.add(handle.getTaskId());
                            consumed.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            consumers.add(consumer);
            consumer.start();
        }

        // 3 个生产者：带充足超时，满时等待消费者取走
        List<Thread> producers = new ArrayList<>(producerCount);
        for (int p = 0; p < producerCount; p++) {
            final int base = p * perProducer;
            Thread producer = new Thread(() -> {
                for (int i = 0; i < perProducer; i++) {
                    try {
                        boolean ok = q.offer(handle((long) (base + i), 5, 0L), 2000L);
                        if (!ok) {
                            throw new IllegalStateException("入队超时，任务被丢弃");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            producers.add(producer);
            producer.start();
        }
        for (Thread producer : producers) {
            producer.join();
        }
        producersDone.set(true);
        for (Thread consumer : consumers) {
            consumer.join();
        }

        assertEquals(total, consumed.get(), "不应丢任务");
        assertEquals(total, consumedIds.size(), "不应有重复消费");
        assertEquals(0, q.size(), "队列应被消费干净");
    }

    /**
     * 构造任务句柄。
     *
     * @param taskId   任务 ID
     * @param priority 优先级
     * @param created  创建时间
     * @return 任务句柄
     */
    private TaskHandle handle(Long taskId, int priority, Long created) {
        return new TaskHandle(taskId, priority, created, taskId);
    }
}
