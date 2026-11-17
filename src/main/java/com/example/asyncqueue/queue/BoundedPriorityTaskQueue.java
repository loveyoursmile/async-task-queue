package com.example.asyncqueue.queue;

import com.example.asyncqueue.domain.TaskHandle;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自研有界优先级任务队列。
 *
 * <p>实现要点：</p>
 * <ul>
 *     <li>底层使用 java.util.PriorityQueue + ReentrantLock + 双 Condition（notEmpty / notFull），
 *         完全基于 JDK 并发原语，不引入任何队列中间件；</li>
 *     <li>容量有界：构造时固定容量 = 核心线程数 × 容量系数；</li>
 *     <li>背压：队列满时 offer 最多等待 timeout，超时返回 false，由上层转换为明确的拒绝响应；</li>
 *     <li>优先级：按 TaskHandle.compareTo 排序，优先级高的先出队，同优先级 FIFO；</li>
 *     <li>可恢复：drainAll 在优雅停机时把未处理任务全部取出，由上层回写数据库状态。</li>
 * </ul>
 *
 * @author async-queue
 */
@Slf4j
public class BoundedPriorityTaskQueue implements TaskQueue {

    private final int capacity;

    /** 按优先级排序的堆，由 lock 保护 */
    private final java.util.PriorityQueue<TaskHandle> heap;

    /** 用于 contains 判断的快速索引，由 lock 保护 */
    private final Set<Long> taskIds = new HashSet<>(64);

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("队列容量必须大于 0");
        }
        this.capacity = capacity;
        this.heap = new java.util.PriorityQueue<>(Math.max(capacity, 16), Comparator.naturalOrder());
    }

    @Override
    public boolean offer(TaskHandle handle, long timeout) throws InterruptedException {
        if (handle == null) {
            throw new IllegalArgumentException("任务句柄不能为 null");
        }
        long nanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeout));
        lock.lockInterruptibly();
        try {
            while (heap.size() >= capacity) {
                if (nanos <= 0L) {
                    log.debug("队列已满，拒绝任务 taskId={}", handle.getTaskId());
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            heap.offer(handle);
            taskIds.add(handle.getTaskId());
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TaskHandle poll(long timeout) throws InterruptedException {
        long nanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeout));
        lock.lockInterruptibly();
        try {
            while (heap.isEmpty()) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            TaskHandle handle = heap.poll();
            taskIds.remove(handle.getTaskId());
            notFull.signal();
            return handle;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TaskHandle> drainAll() {
        lock.lock();
        try {
            List<TaskHandle> drained = new ArrayList<>(heap.size());
            TaskHandle handle;
            while ((handle = heap.poll()) != null) {
                drained.add(handle);
                taskIds.remove(handle.getTaskId());
            }
            notFull.signalAll();
            log.info("优雅停机：从内存队列取出 {} 个未处理任务", drained.size());
            return drained;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return heap.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean contains(Long taskId) {
        if (taskId == null) {
            return false;
        }
        lock.lock();
        try {
            return taskIds.contains(taskId);
        } finally {
            lock.unlock();
        }
    }
}
