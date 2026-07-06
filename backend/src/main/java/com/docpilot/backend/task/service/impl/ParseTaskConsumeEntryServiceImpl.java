package com.docpilot.backend.task.service.impl;

import com.docpilot.backend.ai.service.RagIndexingTriggerService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.SummaryUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.document.parser.ParseResult;
import com.docpilot.backend.document.parser.ParserException;
import com.docpilot.backend.document.parser.ParserInput;
import com.docpilot.backend.document.parser.ParserOptions;
import com.docpilot.backend.document.parser.ParserRegistry;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.mq.entity.ParseTaskConsumeRecord;
import com.docpilot.backend.mq.mapper.ParseTaskConsumeRecordMapper;
import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.ParseTaskConsumeEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ParseTaskConsumeEntryServiceImpl implements ParseTaskConsumeEntryService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskConsumeEntryServiceImpl.class);
    private static final int SUMMARY_MAX_LENGTH = 200;
    private static final int ERROR_MSG_MAX_LENGTH = 512;
    private static final String CONSUME_STATUS_FAILED = "FAILED";

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentMapper documentMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ParserRegistry parserRegistry;
    private final ParseTaskConsumeRecordMapper parseTaskConsumeRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RagIndexingTriggerService ragIndexingTriggerService;
    private final long parserMaxFileSizeBytes;
    private final long parserTimeoutMillis;

    public ParseTaskConsumeEntryServiceImpl(ParseTaskMapper parseTaskMapper,
                                            DocumentMapper documentMapper,
                                            FileRecordMapper fileRecordMapper,
                                            ParserRegistry parserRegistry,
                                            ParseTaskConsumeRecordMapper parseTaskConsumeRecordMapper,
                                            StringRedisTemplate stringRedisTemplate,
                                            RagIndexingTriggerService ragIndexingTriggerService,
                                            @Value("${app.document.parser.max-file-size-bytes:20971520}") long parserMaxFileSizeBytes,
                                            @Value("${app.document.parser.timeout-ms:10000}") long parserTimeoutMillis) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentMapper = documentMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.parserRegistry = parserRegistry;
        this.parseTaskConsumeRecordMapper = parseTaskConsumeRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ragIndexingTriggerService = ragIndexingTriggerService;
        this.parserMaxFileSizeBytes = parserMaxFileSizeBytes;
        this.parserTimeoutMillis = parserTimeoutMillis;
    }

    @Override
    public void handle(ParseTaskMessage message) {
        if (message == null
                || message.getTaskId() == null
                || message.getDocumentId() == null
                || message.getFileRecordId() == null) {
            log.warn("[PARSE_INVALID_MESSAGE] message={}", message);
            if (message != null && message.getTaskId() != null) {
                markFailed(message.getTaskId(), null, message.getDocumentId(), "INVALID_MESSAGE", "parse message missing required fields");
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
            markFailed(parseTask.getId(), parseTask.getUserId(), parseTask.getDocumentId(),
                    "MESSAGE_TASK_MISMATCH", "message ids do not match parse task");
            return;
        }

        Document document = documentMapper.selectById(message.getDocumentId());
        if (document == null) {
            log.warn("[PARSE_DOCUMENT_NOT_FOUND] taskId={}, documentId={}, fileRecordId={}",
                    message.getTaskId(), message.getDocumentId(), message.getFileRecordId());
            markFailed(parseTask.getId(), parseTask.getUserId(), parseTask.getDocumentId(),
                    "DOCUMENT_NOT_FOUND", "document does not exist");
            return;
        }

        FileRecord fileRecord = fileRecordMapper.selectById(message.getFileRecordId());
        if (fileRecord == null) {
            log.warn("[PARSE_FILE_RECORD_NOT_FOUND] taskId={}, documentId={}, fileRecordId={}",
                    message.getTaskId(), message.getDocumentId(), message.getFileRecordId());
            markFailed(parseTask.getId(), document.getUserId(), document.getId(),
                    "FILE_RECORD_NOT_FOUND", "file record does not exist");
            return;
        }

        if (!message.getFileRecordId().equals(document.getFileRecordId())) {
            log.warn("[PARSE_DOCUMENT_FILE_MISMATCH] taskId={}, documentId={}, msgFileRecordId={}, documentFileRecordId={}",
                    message.getTaskId(),
                    message.getDocumentId(),
                    message.getFileRecordId(),
                    document.getFileRecordId());
            markFailed(parseTask.getId(), document.getUserId(), document.getId(),
                    "DOCUMENT_FILE_MISMATCH", "document and file record mismatch");
            return;
        }

        try {
            long uploadedStart = System.nanoTime();
            transitionToStage(parseTask, document, ParseStatusConstants.UPLOADED);
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.UPLOADED, System.nanoTime() - uploadedStart);

            transitionToStage(parseTask, document, ParseStatusConstants.PARSING);
            long parsingStart = System.nanoTime();
            ParseResult parseResult = parseDocument(fileRecord, document);
            String parsedContent = parseResult.fullText();
            DocPilotMetrics.recordParseStageDuration(ParseStatusConstants.PARSING, System.nanoTime() - parsingStart);
            DocPilotMetrics.recordDocumentParserResult(
                    parseResult.parserName(),
                    "success",
                    parseResult.parseDurationMs(),
                    parseResult.extractedChars(),
                    parseResult.pageCount(),
                    parseResult.blockCount(),
                    parseResult.warnings().size()
            );

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
            triggerRagIndexingSafely(document.getUserId(), document.getId(), parseResult);

            log.info("Parse task consume entry accepted. taskId={}, documentId={}, fileRecordId={}, parser={}, contentLength={}, summaryLength={}, blockCount={}, warningCount={}",
                    parseTask.getId(),
                    document.getId(),
                    fileRecord.getId(),
                    parseResult.parserName(),
                    parsedContent.length(),
                    summary.length(),
                    parseResult.blockCount(),
                    parseResult.warnings().size());
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().startsWith("illegal status transition")) {
                return;
            }
            String errorType = ex instanceof ParserException parserException
                    ? "PARSER_" + parserException.getErrorCode().name()
                    : "PARSE_EXCEPTION";
            DocPilotMetrics.recordDocumentParserResult("unknown", "failed", 0, 0, 0, 0, 0);
            log.error("[PARSE_PROCESS_EXCEPTION] taskId={}, documentId={}, fileRecordId={}, errorType={}",
                    parseTask.getId(), document.getId(), fileRecord.getId(), errorType, ex);
            markFailed(parseTask, document.getUserId(), document.getId(), errorType, ex.getMessage());
        }
    }

    private ParseResult parseDocument(FileRecord fileRecord, Document document) {
        ParserInput input = new ParserInput(
                document.getId(),
                fileRecord.getId(),
                fileRecord.getFileName(),
                fileRecord.getFileExt(),
                fileRecord.getContentType(),
                fileRecord.getFileSize(),
                fileRecord.getStoragePath(),
                new ParserOptions(parserMaxFileSizeBytes, parserTimeoutMillis)
        );
        return parserRegistry.parse(input);
    }

    private void triggerRagIndexingSafely(Long userId, Long documentId, ParseResult parseResult) {
        if (ragIndexingTriggerService == null) {
            return;
        }
        try {
            ragIndexingTriggerService.triggerAfterParseSuccess(userId, documentId, parseResult);
        } catch (RuntimeException ex) {
            log.warn("RAG indexing trigger returned an exception after parse success. userId={}, documentId={}, errorType={}",
                    userId, documentId, ex.getClass().getSimpleName());
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
            String transitionError = "illegal status transition: " + currentStatus + " -> " + targetStatus;
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
            log.warn("Document detail cache eviction failed. cacheKey={}", cacheKey, ex);
        }
    }

    private String buildSummary(String content) {
        return SummaryUtils.buildSummary(content, SUMMARY_MAX_LENGTH);
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null) {
            return "unknown error";
        }
        String trimmed = errorMessage.trim();
        if (trimmed.isEmpty()) {
            return "unknown error";
        }
        if (trimmed.length() <= ERROR_MSG_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, ERROR_MSG_MAX_LENGTH);
    }

    private String buildErrorMsg(String errorType, String stage, String errorMessage) {
        String resolvedStage = (stage == null || stage.isBlank()) ? "UNKNOWN" : stage;
        return errorType + " [stage=" + resolvedStage + "]: " + limitError(errorMessage);
    }
}
