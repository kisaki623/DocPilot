package com.docpilot.backend.task.service;

import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.mq.service.ParseTaskOutboxRelayService;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.impl.ParseTaskServiceImpl;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseTaskServiceImplTest {

    @Mock
    private ParseTaskMapper parseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ParseTaskOutboxRelayService parseTaskOutboxRelayService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private ParseTaskServiceImpl buildService() {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().doNothing().when(rLock).unlock();
        try {
            lenient().when(rLock.tryLock(any(Long.class), any())).thenReturn(true);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
        lenient().when(parseTaskOutboxRelayService.appendPending(any(), any(), any(), anyString())).thenReturn(999L);
        ParseTaskServiceImpl service = new ParseTaskServiceImpl(
                parseTaskMapper,
                documentMapper,
                parseTaskOutboxRelayService,
                redissonClient,
                stringRedisTemplate
        );
        ReflectionTestUtils.setField(service, "parseTaskLockFailMessage", "当前文档解析任务处理中，请稍后重试");
        return service;
    }

    @Test
    void shouldCreateParseTaskWhenDocumentIsValid() throws Exception {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        doAnswer(invocation -> {
            ParseTask parseTask = invocation.getArgument(0);
            parseTask.setId(30L);
            return 1;
        }).when(parseTaskMapper).insert(any(ParseTask.class));

        ParseTaskCreateResponse response = parseTaskService.create(20L, 100L);

        assertEquals(30L, response.getTaskId());
        assertEquals(100L, response.getUserId());
        assertEquals(20L, response.getDocumentId());
        assertEquals(11L, response.getFileRecordId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("待解析", response.getStatusLabel());
        assertEquals("任务已创建，等待消息消费", response.getStatusDescription());
        assertEquals(0, response.getRetryCount());

        ArgumentCaptor<ParseTask> captor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        verify(rLock).tryLock(0L, TimeUnit.SECONDS);
        verify(parseTaskOutboxRelayService).appendPending(30L, 20L, 11L, "create");
    }

    @Test
    void shouldThrowWhenDocumentNotFound() {
        ParseTaskServiceImpl parseTaskService = buildService();
        when(documentMapper.selectById(20L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));
    }

    @Test
    void shouldThrowWhenDocumentBelongsToAnotherUser() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(200L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));
    }

    @Test
    void shouldThrowWhenInsertParseTaskFailed() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);
        when(parseTaskMapper.insert(any(ParseTask.class))).thenThrow(new RuntimeException("db error"));

        assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));
    }

    @Test
    void shouldStillCreateWhenOutboxAppendSucceeds() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        doAnswer(invocation -> {
            ParseTask parseTask = invocation.getArgument(0);
            parseTask.setId(30L);
            return 1;
        }).when(parseTaskMapper).insert(any(ParseTask.class));
        ParseTaskCreateResponse response = parseTaskService.create(20L, 100L);

        assertEquals(30L, response.getTaskId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("待解析", response.getStatusLabel());
        verify(parseTaskOutboxRelayService).appendPending(30L, 20L, 11L, "create");
    }

    @Test
    void shouldRejectCreateWhenLatestTaskIsFailed() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask existingTask = new ParseTask();
        existingTask.setId(30L);
        existingTask.setUserId(100L);
        existingTask.setDocumentId(20L);
        existingTask.setFileRecordId(11L);
        existingTask.setStatus("FAILED");
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(existingTask);

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_RETRY_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldRetryFailedTaskByDocumentId() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask failedTask = new ParseTask();
        failedTask.setId(30L);
        failedTask.setUserId(100L);
        failedTask.setDocumentId(20L);
        failedTask.setFileRecordId(11L);
        failedTask.setStatus("FAILED");
        failedTask.setRetryCount(0);
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(failedTask);
        when(parseTaskMapper.resetFailedTaskForRetry(30L, 100L, 1)).thenReturn(1);

        ParseTaskCreateResponse response = parseTaskService.retry(20L, 100L);

        assertEquals(30L, response.getTaskId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("待解析", response.getStatusLabel());
        assertEquals(Boolean.FALSE, response.getReused());
        assertEquals(1, response.getRetryCount());
        verify(parseTaskMapper).resetFailedTaskForRetry(30L, 100L, 1);
        verify(documentMapper).updateById(any(Document.class));
        verify(stringRedisTemplate).delete("docpilot:document:detail:u:100:d:20");
        try {
            verify(rLock).tryLock(0L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
        verify(parseTaskOutboxRelayService).appendPending(30L, 20L, 11L, "retry");
    }

    @Test
    void shouldReparseWhenLatestTaskIsSuccess() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask latestTask = new ParseTask();
        latestTask.setId(30L);
        latestTask.setUserId(100L);
        latestTask.setDocumentId(20L);
        latestTask.setFileRecordId(11L);
        latestTask.setStatus("SUCCESS");
        latestTask.setRetryCount(2);
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(latestTask);
        when(parseTaskMapper.resetTerminalTaskForReparse(30L, 100L)).thenReturn(1);

        ParseTaskCreateResponse response = parseTaskService.reparse(20L, 100L);

        assertEquals(30L, response.getTaskId());
        assertEquals("PENDING", response.getStatus());
        assertEquals(2, response.getRetryCount());
        verify(parseTaskMapper).resetTerminalTaskForReparse(30L, 100L);
        verify(documentMapper).updateById(any(Document.class));
        verify(stringRedisTemplate).delete("docpilot:document:detail:u:100:d:20");
        verify(parseTaskOutboxRelayService).appendPending(30L, 20L, 11L, "reparse");
    }

    @Test
    void shouldRejectReparseWhenLatestTaskIsProcessing() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask task = new ParseTask();
        task.setId(30L);
        task.setUserId(100L);
        task.setDocumentId(20L);
        task.setFileRecordId(11L);
        task.setStatus("PARSING");
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.reparse(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_REPARSE_NOT_ALLOWED, ex.getErrorCode());
        verify(parseTaskMapper, never()).resetTerminalTaskForReparse(any(), any());
        verify(parseTaskOutboxRelayService, never()).appendPending(any(), any(), any(), anyString());
    }

    @Test
    void shouldRejectRetryWhenLatestTaskIsNotFailed() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask task = new ParseTask();
        task.setId(30L);
        task.setUserId(100L);
        task.setDocumentId(20L);
        task.setFileRecordId(11L);
        task.setStatus("SUCCESS");
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.retry(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_RETRY_NOT_ALLOWED, ex.getErrorCode());
        verify(parseTaskMapper, never()).resetFailedTaskForRetry(any(), any(), any(Integer.class));
        verify(parseTaskOutboxRelayService, never()).appendPending(any(), any(), any(), anyString());
    }

    @Test
    void shouldThrowWhenRetryTaskNotFound() {
        ParseTaskServiceImpl parseTaskService = buildService();

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.retry(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldRejectCreateWhenLockNotAcquired() {
        ParseTaskServiceImpl parseTaskService = buildService();
        try {
            when(rLock.tryLock(any(Long.class), any())).thenReturn(false);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_LOCKED, ex.getErrorCode());
        verify(parseTaskMapper, never()).insert(any(ParseTask.class));
    }

    @Test
    void shouldThrowBusinessErrorWhenLockInterrupted() throws Exception {
        ParseTaskServiceImpl parseTaskService = buildService();
        when(rLock.tryLock(any(Long.class), any())).thenThrow(new InterruptedException("interrupted"));

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.create(20L, 100L));

        assertEquals(ErrorCode.BUSINESS_ERROR, ex.getErrorCode());
        assertEquals("解析任务锁获取被中断", ex.getMessage());
        verify(parseTaskMapper, never()).insert(any(ParseTask.class));
    }

    @Test
    void shouldRejectRetryWhenLockNotAcquired() {
        ParseTaskServiceImpl parseTaskService = buildService();
        try {
            when(rLock.tryLock(any(Long.class), any())).thenReturn(false);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> parseTaskService.retry(20L, 100L));

        assertEquals(ErrorCode.PARSE_TASK_LOCKED, ex.getErrorCode());
        verify(parseTaskMapper, never()).resetFailedTaskForRetry(any(), any(), any(Integer.class));
    }

    @Test
    void shouldNotUnlockWhenLockNotHeldByCurrentThread() {
        ParseTaskServiceImpl parseTaskService = buildService();
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(11L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        ParseTask existingTask = new ParseTask();
        existingTask.setId(30L);
        existingTask.setUserId(100L);
        existingTask.setDocumentId(20L);
        existingTask.setFileRecordId(11L);
        existingTask.setStatus("SUCCESS");
        when(parseTaskMapper.selectLatestByUserAndDocumentId(100L, 20L)).thenReturn(existingTask);

        ParseTaskCreateResponse response = parseTaskService.create(20L, 100L);

        assertEquals(30L, response.getTaskId());
        verify(rLock, never()).unlock();
    }
}

