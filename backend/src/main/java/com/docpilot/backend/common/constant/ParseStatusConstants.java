package com.docpilot.backend.common.constant;

import java.util.Set;

public final class ParseStatusConstants {

    public static final String PENDING = "PENDING";
    public static final String UPLOADED = "UPLOADED";
    public static final String PARSING = "PARSING";
    public static final String SPLITTING = "SPLITTING";
    public static final String SUMMARIZING = "SUMMARIZING";
    public static final String INDEXING = "INDEXING";

    // 兼容历史数据，PROCESSING 在 10.1 后视为旧状态别名。
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public static final String LABEL_PENDING = "待解析";
    public static final String LABEL_UPLOADED = "已入队";
    public static final String LABEL_PARSING = "解析中";
    public static final String LABEL_SPLITTING = "切片中";
    public static final String LABEL_SUMMARIZING = "摘要生成中";
    public static final String LABEL_INDEXING = "索引构建中";
    public static final String LABEL_PROCESSING = "解析中";
    public static final String LABEL_SUCCESS = "解析成功";
    public static final String LABEL_FAILED = "解析失败";
    public static final String LABEL_UNKNOWN = "未知状态";

    private static final Set<String> STAGE_PROCESSING_STATUSES = Set.of(
            UPLOADED,
            PARSING,
            SPLITTING,
            SUMMARIZING,
            INDEXING,
            PROCESSING
    );

    private ParseStatusConstants() {
    }

    public static String toLabel(String parseStatus) {
        if (PENDING.equals(parseStatus)) {
            return LABEL_PENDING;
        }
        if (UPLOADED.equals(parseStatus)) {
            return LABEL_UPLOADED;
        }
        if (PARSING.equals(parseStatus)) {
            return LABEL_PARSING;
        }
        if (SPLITTING.equals(parseStatus)) {
            return LABEL_SPLITTING;
        }
        if (SUMMARIZING.equals(parseStatus)) {
            return LABEL_SUMMARIZING;
        }
        if (INDEXING.equals(parseStatus)) {
            return LABEL_INDEXING;
        }
        if (PROCESSING.equals(parseStatus)) {
            return LABEL_PROCESSING;
        }
        if (SUCCESS.equals(parseStatus)) {
            return LABEL_SUCCESS;
        }
        if (FAILED.equals(parseStatus)) {
            return LABEL_FAILED;
        }
        return LABEL_UNKNOWN;
    }

    public static String toStageDescription(String parseStatus) {
        if (PENDING.equals(parseStatus)) {
            return "任务已创建，等待消息消费";
        }
        if (UPLOADED.equals(parseStatus)) {
            return "消息已消费，准备执行解析";
        }
        if (PARSING.equals(parseStatus) || PROCESSING.equals(parseStatus)) {
            return "正在读取并解析文档内容";
        }
        if (SPLITTING.equals(parseStatus)) {
            return "正在进行最小切片处理（当前阶段为预留逻辑）";
        }
        if (SUMMARIZING.equals(parseStatus)) {
            return "正在基于解析内容生成摘要";
        }
        if (INDEXING.equals(parseStatus)) {
            return "正在写入结构化结果并收口任务";
        }
        if (SUCCESS.equals(parseStatus)) {
            return "解析完成，可查看文档详情并发起问答";
        }
        if (FAILED.equals(parseStatus)) {
            return "解析失败，可查看失败原因并按规则重试";
        }
        return "状态未知，请刷新后重试";
    }

    public static boolean isTerminal(String parseStatus) {
        return SUCCESS.equals(parseStatus) || FAILED.equals(parseStatus);
    }

    public static boolean isRetryAllowed(String parseStatus) {
        return FAILED.equals(parseStatus);
    }

    public static boolean isReparseAllowed(String parseStatus) {
        return SUCCESS.equals(parseStatus) || FAILED.equals(parseStatus);
    }

    public static boolean isProcessingStage(String parseStatus) {
        return STAGE_PROCESSING_STATUSES.contains(parseStatus);
    }

    public static boolean canTransit(String fromStatus, String toStatus) {
        if (toStatus == null || toStatus.isBlank()) {
            return false;
        }
        if (fromStatus == null || fromStatus.isBlank()) {
            return PENDING.equals(toStatus);
        }
        if (fromStatus.equals(toStatus)) {
            return true;
        }
        if (PENDING.equals(fromStatus)) {
            return UPLOADED.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (UPLOADED.equals(fromStatus)) {
            return PARSING.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (PARSING.equals(fromStatus)) {
            return SPLITTING.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (SPLITTING.equals(fromStatus)) {
            return SUMMARIZING.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (SUMMARIZING.equals(fromStatus)) {
            return INDEXING.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (INDEXING.equals(fromStatus)) {
            return SUCCESS.equals(toStatus) || FAILED.equals(toStatus);
        }
        if (PROCESSING.equals(fromStatus)) {
            return PARSING.equals(toStatus)
                    || SPLITTING.equals(toStatus)
                    || SUMMARIZING.equals(toStatus)
                    || INDEXING.equals(toStatus)
                    || SUCCESS.equals(toStatus)
                    || FAILED.equals(toStatus);
        }
        return false;
    }
}

