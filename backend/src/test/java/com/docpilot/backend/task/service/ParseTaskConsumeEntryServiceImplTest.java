package com.docpilot.backend.task.service;

import com.docpilot.backend.common.constant.CommonConstants;
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
import com.docpilot.backend.task.service.impl.ParseTaskConsumeEntryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseTaskConsumeEntryServiceImplTest {

    @Mock
    private ParseTaskMapper parseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private FileRecordMapper fileRecordMapper;

    @Mock
    private FileContentReader fileContentReader;

    @Mock
    private ParseTaskConsumeRecordMapper parseTaskConsumeRecordMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private ParseTaskConsumeEntryServiceImpl buildService() {
        lenient().when(parseTaskConsumeRecordMapper.insertProcessing(anyString(), anyLong())).thenReturn(1);
        return new ParseTaskConsumeEntryServiceImpl(
                parseTaskMapper,
                documentMapper,
                fileRecordMapper,
                fileContentReader,
                parseTaskConsumeRecordMapper,
                stringRedisTemplate
        );
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipWhenMessageMissingKeyFields() {
        ParseTaskConsumeEntryServiceImpl service = buildService();
        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(1L);

        service.handle(message);

        verify(parseTaskMapper, never()).selectById(1L);
        verify(parseTaskConsumeRecordMapper, never()).insertProcessing(anyString(), anyLong());
        verify(parseTaskMapper).updateById(any(ParseTask.class));
        verify(documentMapper, never()).updateById(any(Document.class));
    }

    @Test
    void shouldParseTxtAndUpdateDocumentAndTaskStatus() throws IOException {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        Path filePath = tempDir.resolve("sample.txt");
        Files.writeString(filePath, "DocPilot parse content test");

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(1L);
        parseTask.setDocumentId(2L);
        parseTask.setFileRecordId(3L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(1L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(2L);
        document.setUserId(100L);
        document.setFileRecordId(3L);
        when(documentMapper.selectById(2L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(3L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("sample.txt");
        fileRecord.setStoragePath(filePath.toString());
        when(fileRecordMapper.selectById(3L)).thenReturn(fileRecord);
        when(fileContentReader.readText(filePath.toString())).thenReturn("DocPilot parse content test");

        service.handle(message);

        verify(parseTaskMapper).selectById(1L);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(6)).updateById(taskCaptor.capture());
        List<ParseTask> taskUpdates = taskCaptor.getAllValues();
        assertEquals("UPLOADED", taskUpdates.get(0).getStatus());
        assertEquals("SUCCESS", taskUpdates.get(taskUpdates.size() - 1).getStatus());
        assertTrue(taskUpdates.stream().anyMatch(task -> "PARSING".equals(task.getStatus())));
        assertTrue(taskUpdates.stream().anyMatch(task -> "SPLITTING".equals(task.getStatus())));
        assertTrue(taskUpdates.stream().anyMatch(task -> "SUMMARIZING".equals(task.getStatus())));
        assertTrue(taskUpdates.stream().anyMatch(task -> "INDEXING".equals(task.getStatus())));

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper, org.mockito.Mockito.times(6)).updateById(documentCaptor.capture());
        List<Document> docUpdates = documentCaptor.getAllValues();
        assertEquals("UPLOADED", docUpdates.get(0).getParseStatus());
        Document successDocument = docUpdates.get(docUpdates.size() - 1);
        assertEquals("SUCCESS", successDocument.getParseStatus());
        assertEquals("DocPilot parse content test", successDocument.getContent());
        assertEquals("DocPilot parse content test", successDocument.getSummary());
        verify(stringRedisTemplate, org.mockito.Mockito.times(6))
                .delete(CommonConstants.buildDocumentDetailCacheKey(100L, 2L));
    }

    @Test
    void shouldWritePdfPlaceholderWhenPdfMessageConsumed() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(11L);
        message.setDocumentId(22L);
        message.setFileRecordId(33L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(11L);
        parseTask.setDocumentId(22L);
        parseTask.setFileRecordId(33L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(11L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(22L);
        document.setUserId(100L);
        document.setFileRecordId(33L);
        when(documentMapper.selectById(22L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(33L);
        fileRecord.setFileExt("pdf");
        fileRecord.setFileName("demo.pdf");
        fileRecord.setStoragePath("ignored-in-phase-4.2");
        when(fileRecordMapper.selectById(33L)).thenReturn(fileRecord);

        service.handle(message);

        ArgumentCaptor<Document> updateCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper, org.mockito.Mockito.times(6)).updateById(updateCaptor.capture());
        Document successUpdated = updateCaptor.getAllValues().get(updateCaptor.getAllValues().size() - 1);
        assertTrue(successUpdated.getContent().contains("暂未实现 PDF 真实解析"));
        assertTrue(successUpdated.getSummary().contains("暂未实现 PDF 真实解析"));
    }

    @Test
    void shouldSkipWhenMessageAndTaskNotMatched() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(1L);
        parseTask.setUserId(100L);
        parseTask.setDocumentId(20L);
        parseTask.setFileRecordId(30L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(1L)).thenReturn(parseTask);

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
        verify(documentMapper).updateById(any(Document.class));
    }

    @Test
    void shouldMarkFailedWhenSourceFileMissing() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(10L);
        message.setDocumentId(20L);
        message.setFileRecordId(30L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(10L);
        parseTask.setDocumentId(20L);
        parseTask.setFileRecordId(30L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(10L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(20L);
        document.setUserId(100L);
        document.setFileRecordId(30L);
        when(documentMapper.selectById(20L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(30L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("missing.txt");
        fileRecord.setStoragePath(tempDir.resolve("missing.txt").toString());
        when(fileRecordMapper.selectById(30L)).thenReturn(fileRecord);
        when(fileContentReader.readText(fileRecord.getStoragePath()))
                .thenThrow(new IllegalStateException("源文件不存在"));

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(3)).updateById(taskCaptor.capture());
        assertEquals("UPLOADED", taskCaptor.getAllValues().get(0).getStatus());
        assertEquals("PARSING", taskCaptor.getAllValues().get(1).getStatus());
        assertEquals("FAILED", taskCaptor.getAllValues().get(2).getStatus());
    }

    @Test
    void shouldMarkDocumentFailedWhenParseTaskNotFound() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(100L);
        message.setDocumentId(200L);
        message.setFileRecordId(300L);
        when(parseTaskMapper.selectById(100L)).thenReturn(null);

        service.handle(message);

        verify(parseTaskMapper, never()).updateById(any(ParseTask.class));
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(documentCaptor.capture());
        assertEquals(200L, documentCaptor.getValue().getId());
        assertEquals("FAILED", documentCaptor.getValue().getParseStatus());
    }

    @Test
    void shouldMarkFailedWhenDocumentNotFound() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(1L);
        parseTask.setUserId(100L);
        parseTask.setDocumentId(2L);
        parseTask.setFileRecordId(3L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(1L)).thenReturn(parseTask);
        when(documentMapper.selectById(2L)).thenReturn(null);

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
    }

    @Test
    void shouldMarkFailedWhenFileRecordNotFound() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(1L);
        parseTask.setUserId(100L);
        parseTask.setDocumentId(2L);
        parseTask.setFileRecordId(3L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(1L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(2L);
        document.setUserId(100L);
        document.setFileRecordId(3L);
        when(documentMapper.selectById(2L)).thenReturn(document);
        when(fileRecordMapper.selectById(3L)).thenReturn(null);

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
    }

    @Test
    void shouldMarkFailedWhenFileTypeUnsupported() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(7L);
        message.setDocumentId(8L);
        message.setFileRecordId(9L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(7L);
        parseTask.setUserId(100L);
        parseTask.setDocumentId(8L);
        parseTask.setFileRecordId(9L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(7L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(8L);
        document.setUserId(100L);
        document.setFileRecordId(9L);
        when(documentMapper.selectById(8L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(9L);
        fileRecord.setFileExt("docx");
        fileRecord.setFileName("demo.docx");
        fileRecord.setStoragePath("ignored");
        when(fileRecordMapper.selectById(9L)).thenReturn(fileRecord);

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(3)).updateById(taskCaptor.capture());
        assertEquals("UPLOADED", taskCaptor.getAllValues().get(0).getStatus());
        assertEquals("PARSING", taskCaptor.getAllValues().get(1).getStatus());
        assertEquals("FAILED", taskCaptor.getAllValues().get(2).getStatus());
    }

    @Test
    void shouldInterceptIllegalStatusTransition() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(51L);
        message.setDocumentId(52L);
        message.setFileRecordId(53L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(51L);
        parseTask.setUserId(100L);
        parseTask.setDocumentId(52L);
        parseTask.setFileRecordId(53L);
        parseTask.setStatus("SUMMARIZING");
        when(parseTaskMapper.selectById(51L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(52L);
        document.setUserId(100L);
        document.setFileRecordId(53L);
        when(documentMapper.selectById(52L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(53L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("demo.txt");
        fileRecord.setStoragePath(tempDir.resolve("demo.txt").toString());
        when(fileRecordMapper.selectById(53L)).thenReturn(fileRecord);

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
        assertTrue(taskCaptor.getValue().getErrorMsg().contains("ILLEGAL_STATUS_TRANSITION"));
    }

    @Test
    void shouldSkipWhenTaskAlreadyTerminal() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(61L);
        message.setDocumentId(62L);
        message.setFileRecordId(63L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(61L);
        parseTask.setStatus("SUCCESS");
        when(parseTaskMapper.selectById(61L)).thenReturn(parseTask);

        service.handle(message);

        verify(parseTaskMapper, never()).updateById(any(ParseTask.class));
        verify(documentMapper, never()).updateById(any(Document.class));
    }

    @Test
    void shouldSkipDuplicateMessageWhenConsumeRecordAlreadySuccess() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setMessageKey("parse-task:1:create:test");
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        when(parseTaskConsumeRecordMapper.insertProcessing("parse-task:1:create:test", 1L)).thenReturn(0);
        ParseTaskConsumeRecord consumeRecord = new ParseTaskConsumeRecord();
        consumeRecord.setMessageKey("parse-task:1:create:test");
        consumeRecord.setStatus("SUCCESS");
        when(parseTaskConsumeRecordMapper.selectByMessageKey("parse-task:1:create:test")).thenReturn(consumeRecord);

        service.handle(message);

        verify(parseTaskMapper, never()).selectById(1L);
        verify(parseTaskMapper, never()).updateById(any(ParseTask.class));
        verify(documentMapper, never()).updateById(any(Document.class));
    }
}

