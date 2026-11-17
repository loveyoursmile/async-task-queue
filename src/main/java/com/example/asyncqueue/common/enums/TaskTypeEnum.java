package com.example.asyncqueue.common.enums;

import java.util.Arrays;
import java.util.Objects;

/**
 * 任务类型枚举，不同类型对应不同的耗时特征。
 *
 * @author async-queue
 */
public enum TaskTypeEnum {

    /** LLM 文档摘要：IO 密集型，主要耗时在等待模型服务返回 */
    DOCUMENT_SUMMARY("文档摘要", 400L, 1200L, 10),
    /** 向量化嵌入：CPU 与 IO 混合型 */
    VECTOR_EMBEDDING("向量嵌入", 200L, 600L, 5),
    /** 本地规则校验：CPU 密集型 */
    RULE_CHECK("规则校验", 50L, 150L, 0);

    private final String description;
    private final long minCostMs;
    private final long maxCostMs;
    private final int defaultFailPercent;

    TaskTypeEnum(String description, long minCostMs, long maxCostMs, int defaultFailPercent) {
        this.description = description;
        this.minCostMs = minCostMs;
        this.maxCostMs = maxCostMs;
        this.defaultFailPercent = defaultFailPercent;
    }

    public String getDescription() {
        return description;
    }

    public long getMinCostMs() {
        return minCostMs;
    }

    public long getMaxCostMs() {
        return maxCostMs;
    }

    public int getDefaultFailPercent() {
        return defaultFailPercent;
    }

    /**
     * 根据名称解析枚举，未匹配返回 null。
     *
     * @param name 枚举名称
     * @return 任务类型，未匹配为 null
     */
    public static TaskTypeEnum of(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> Objects.equals(type.name(), name))
                .findFirst()
                .orElse(null);
    }
}
