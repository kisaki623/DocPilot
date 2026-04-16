package com.docpilot.backend.task.service.impl;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.SummaryUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.file.storage.FileContentReader;
import com.docpilot.backend.mq.entity.ParseTaskConsumeRecord;
import com.docpilot.backend.mq.mapper.ParseTaskConsumeRecordMapper;
import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.ParseTaskConsumeEntryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ParseTaskConsumeEntryServiceImpl implements ParseTaskConsumeEntryService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskConsumeEntryServiceImpl.class);
    private static final int SUMMARY_MAX_LENGTH = 200;
    private static final int ERROR_MSG_MAX_LENGTH = 512;
    private static final String PDF_PLACEHOLDER_CONTENT = "当前阶段（4.2）暂未实现 PDF 真实解析。";
    private static final String CONSUME_STATUS_FAILED = "FAILED";

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentMapper documentMapper;
    private final FileRecordMapper fileRecordMapper;
    private final FileContentReader fileContentReader;
    private final ParseTaskConsumeRecordMapper parseTaskConsumeRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public ParseTaskConsumeEntryServiceImpl(ParseTaskMapper parseTaskMapper,
                                            DocumentMapper documentMapper,
                                            FileRecordMapper fileRecordMapper,
                                            FileContentReader fileContentReader,
                                            ParseTaskConsumeRecordMapper parseTaskConsumeRecordMapper,
                                            StringRedisTemplate stringRedisTemplate) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentMapper = documentMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.fileContentReader = fileContentReader;
        this.parseTaskConsumeRecordMapper = parseTaskConsumeRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void handle(ParseTaskMessage message) {
        if (message == null
                || message.getTaskId() == null
                || message.getDocumentId() == null
                || message.getFileRecordId() == null) {
            log.warn("[PARSE_INVALID_MESSAGE] message={}", message);
            if (message != null && message.getTaskId() != null) {
                markFailed(message.getTaskId(), null, message.getDocumentId(), "INVALID_MESSAGE", "解析任务消息字段缺失");
            }
            return;
        }

        String messageKey = resolveMessageKey(message);
        if (!claimConsume(messageKey, message.getTaskId())) {
            log.info("[PARSE_DUPLICATE_MESSAGE_SKIP] messageKey={}, taskId={}", messageKey, message.getTaskId());
            return;
        }

        try {
            doHandle(message);
            markConsumeSuccess(messageKey);
        } catch (Exception ex) {
            markConsumeFailed(messageKey, ex.getMessage());
            throw ex;
        }
    }

    private void doHandle(ParseTaskMessage message) {
        ParseTask parseTask = parseTaskMapper.selectById(message.getTaskId());
        if (parseTask == null) {
            log.warn("[PARSE_TASK_NOT_FOUND] taskId={}, documentId={}, fileRecordId={}",
                    message.getTaskId(), message.getDocumentId(), message.getFileRecordId());
            markDocumentFailed(message.getDocumentId());
            return;
        }

        if (ParseStatusConstants.isTerminal(parseTask.getStatus())) {
            log.info("[PARSE_TASK_TERMINAL_SKIP] taskId={}, status={}", parseTask.getId(), parseTask.getStatus());
            return;
        }

        if (!message.getDocumentId().equals(parseTask.getDocumentId())
                || !message.getFileRecordId().equals(parseTask.getFileRecordId())) {
            log.warn("[PARSE_MESSAGE_TASK_MISMATCH] taskId={}, msgDocumentId={}, dbDocumentId={}, msgFileRecordId={}, dbFileRecordId={}",
                    message.getTaskId(),
                    message.getDocumentId(),
                    parseTask.getDocumentId(),
                    message.getFileRecordId(),
                    parseTask.getFileRecordId());
            markFailed(parseTask.getId(), parseTask.getUserId(), parseTask.getDocumentId(), "MESSAGE_TASK_MISMATCH", "消息中的 ID 与解析任务不一致");
            return;
        }

        Document document = documentMapper.selectById(message.getDocumentId());
        if (document == null) {
            log.warn("[PARSE_DOCUMENT_NOT_FOUND] taskId={}, documentId={}, fileRecordId={}",
                    message.getTaskId(), message.getDocumentId(), message.getFileRecordId());
            markFailed(parseTask.getId(), parseTask.getUserId(), parseTask.getDocumentId(), "DOCUMENT_NOT_FOUND", "文档不存在");
            return;
        }

        FileRecord fileRecord = fileRecordMapper.selectById(message.getFileRecordId());
        if (fileRecord == null) {
            log.warn("[PARSE_FILE_RECORD_NOT_FOUND] taskId={}, documentId={}, fileRecordId={}",
                    message.getTaskId(), message.getDocumentId(), message.getFileRecordId());
            markFailed(parseTask.getId(), document.getUserId(), document.getId(), "FILE_RECORD_NOT_FOUND", "文件记录不存在");
            return;
        }

        if (!message.getFileRecordId().equals(document.getFileRecordId())) {
            log.warn("[PARSE_DOCUMENT_FILE_MISMATCH] taskId={}, documentId={}, msgFileRecordId={}, documentFileRecordId={}",
                    message.getTaskId(),
                    message.getDocumentId(),
                    message.getFileRecordId(),
                    document.getFileRecordId());
            markFailed(parseTask.getId(), document.getUserId(), document.getId(), "DOCUMENT_FILE_MISMATCH", "文档与文件记录不匹配");
            return;
        }

        try {
            long uploadedStart = System.nanoTime();
            transitionToStage(parseTask, document, ParseStatusConstants.UPLOADED);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.UPLOADED, System.nanoTime() - uploadedStart);

            transitionToStage(parseTask, document, ParseStatusConstants.PARSING);
            long parsingStart = System.nanoTime();
            String parsedContent = parseContentByFileType(fileRecord);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.PARSING, System.nanoTime() - parsingStart);

            long splittingStart = System.nanoTime();
            transitionToStage(parseTask, document, ParseStatusConstants.SPLITTING);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.SPLITTING, System.nanoTime() - splittingStart);

            transitionToStage(parseTask, document, ParseStatusConstants.SUMMARIZING);
            long summarizingStart = System.nanoTime();
            String summary = buildSummary(parsedContent);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.SUMMARIZING, System.nanoTime() - summarizingStart);

            long indexingStart = System.nanoTime();
            transitionToStage(parseTask, document, ParseStatusConstants.INDEXING);

            Document successDocument = new Document();
            successDocument.setId(document.getId());
            successDocument.setContent(parsedContent);
            successDocument.setSummary(summary);
            successDocument.setParseStatus(ParseStatusConstants.SUCCESS);
            documentMapper.updateById(successDocument);
            evictDocumentDetailCache(document.getUserId(), document.getId());

            ParseTask successTask = new ParseTask();
            successTask.setId(parseTask.getId());
            successTask.setStatus(ParseStatusConstants.SUCCESS);
            successTask.setFinishTime(LocalDateTime.now());
            successTask.setErrorMsg(null);
            parseTaskMapper.updateById(successTask);
            parseTask.setStatus(ParseStatusConstants.SUCCESS);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.INDEXING, System.nanoTime() - indexingStart);

            log.info("Parse task consume entry accepted. taskId={}, documentId={}, fileRecordId={}, contentLength={}, summaryLength={}",
                    parseTask.getId(),
                    document.getId(),
                    fileRecord.getId(),
                    parsedContent.length(),
                    summary.length());
        } catch (Exception ex) {
            log.error("[PARSE_PROCESS_EXCEPTION] taskId={}, documentId={}, fileRecordId={}",
                    parseTask.getId(),
                    document.getId(),
                    fileRecord.getId(),
                    ex);
            if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().startsWith("非法状态流转")) {
                return;
            }
            markFailed(parseTask, document.getUserId(), document.getId(), "PARSE_EXCEPTION", ex.getMessage());
        }
    }

    private String resolveMessageKey(ParseTaskMessage message) {
        String messageKey = message.getMessageKey();
        if (messageKey == null || messageKey.isBlank()) {
            return "legacy-task:" + message.getTaskId();
        }
        return messageKey.trim();
    }

    private boolean claimConsume(String messageKey, Long taskId) {
        try {
            if (parseTaskConsumeRecordMapper.insertProcessing(messageKey, taskId) > 0) {
                return true;
            }
        } catch (Exception ignored) {
            // Duplicate message_key will fail insert and continue to status check path.
        }

        ParseTaskConsumeRecord consumeRecord = parseTaskConsumeRecordMapper.selectByMessageKey(messageKey);
        if (consumeRecord == null) {
            return false;
        }
        if (CONSUME_STATUS_FAILED.equals(consumeRecord.getStatus())) {
            return parseTaskConsumeRecordMapper.takeoverFailed(messageKey) > 0;
        }
        return false;
    }

    private void markConsumeSuccess(String messageKey) {
        parseTaskConsumeRecordMapper.markSuccess(messageKey);
    }

    private void markConsumeFailed(String messageKey, String errorMessage) {
        parseTaskConsumeRecordMapper.markFailed(messageKey, limitError(errorMessage));
    }

    private void transitionToStage(ParseTask parseTask, Document document, String targetStatus) {
        String currentStatus = parseTask.getStatus();
        if (!ParseStatusConstants.canTransit(currentStatus, targetStatus)) {
            String transitionError = "非法状态流转: " + currentStatus + " -> " + targetStatus;
            markFailed(parseTask, document.getUserId(), document.getId(), "ILLEGAL_STATUS_TRANSITION", transitionError);
            throw new IllegalStateException(transitionError);
        }

        ParseTask task = new ParseTask();
        task.setId(parseTask.getId());
        task.setStatus(targetStatus);
        if (parseTask.getStartTime() == null && ParseStatusConstants.UPLOADED.equals(targetStatus)) {
            LocalDateTime now = LocalDateTime.now();
            task.setStartTime(now);
            parseTask.setStartTime(now);
        }
        parseTaskMapper.updateById(task);

        Document stageDocument = new Document();
        stageDocument.setId(document.getId());
        stageDocument.setParseStatus(targetStatus);
        documentMapper.updateById(stageDocument);
        evictDocumentDetailCache(document.getUserId(), document.getId());

        parseTask.setStatus(targetStatus);
    }

    private void markFailed(ParseTask parseTask, Long userId, Long documentId, String errorType, String errorMessage) {
        String stage = parseTask == null ? null : parseTask.getStatus();
        ParseTask task = new ParseTask();
        task.setId(parseTask == null ? null : parseTask.getId());
        task.setStatus(ParseStatusConstants.FAILED);
        task.setFinishTime(LocalDateTime.now());
        task.setErrorMsg(limitError(buildErrorMsg(errorType, stage, errorMessage)));
        if (task.getId() != null) {
            parseTaskMapper.updateById(task);
        }

        if (documentId != null) {
            Document document = new Document();
            document.setId(documentId);
            document.setParseStatus(ParseStatusConstants.FAILED);
            documentMapper.updateById(document);
            evictDocumentDetailCache(userId, documentId);
        }
    }

    private void markFailed(Long taskId, Long userId, Long documentId, String errorType, String errorMessage) {
        ParseTask task = new ParseTask();
        task.setId(taskId);
        markFailed(task, userId, documentId, errorType, errorMessage);
    }

    private void markDocumentFailed(Long documentId) {
        if (documentId == null) {
            return;
        }
        Document document = new Document();
        document.setId(documentId);
        document.setParseStatus(ParseStatusConstants.FAILED);
        documentMapper.updateById(document);
    }

    private void evictDocumentDetailCache(Long userId, Long documentId) {
        if (userId == null || documentId == null) {
            return;
        }
        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(userId, documentId);
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception ex) {
            log.warn("文档详情缓存失效失败，cacheKey={}", cacheKey, ex);
        }
    }

    private String parseContentByFileType(FileRecord fileRecord) {
        String extension = safeExtension(fileRecord.getFileExt(), fileRecord.getFileName());
        if ("txt".equals(extension) || "md".equals(extension)) {
            return readTextFile(fileRecord.getStoragePath());
        }
        if ("pdf".equals(extension)) {
            return PDF_PLACEHOLDER_CONTENT;
        }
        log.warn("[PARSE_UNSUPPORTED_FILE_TYPE] fileRecordId={}, ext={}",
                fileRecord.getId(), extension);
        throw new UnsupportedOperationException("不支持的文件类型: " + extension);
    }

    private String readTextFile(String storagePath) {
        return fileContentReader.readText(storagePath);
    }

    private String safeExtension(String fileExt, String fileName) {
        if (fileExt != null && !fileExt.trim().isEmpty()) {
            return fileExt.trim().toLowerCase(Locale.ROOT);
        }
        if (fileName == null) {
            return "unknown";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildSummary(String content) {
        return SummaryUtils.buildSummary(content, SUMMARY_MAX_LENGTH);
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null) {
            return "未知错误";
        }
        String trimmed = errorMessage.trim();
        if (trimmed.isEmpty()) {
            return "未知错误";
        }
        if (trimmed.length() <= ERROR_MSG_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, ERROR_MSG_MAX_LENGTH);
    }

    private String buildErrorMsg(String errorType, String stage, String errorMessage) {
        String resolvedStage = (stage == null || stage.isBlank()) ? "UNKNOWN" : stage;
        return errorType + " [stage=" + resolvedStage + "]: " + errorMessage;
    }
}

