package com.docpilot.backend.task.service;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.document.parser.DocxDocumentParser;
import com.docpilot.backend.document.parser.HtmlDocumentParser;
import com.docpilot.backend.document.parser.ParserRegistry;
import com.docpilot.backend.document.parser.ParseResult;
import com.docpilot.backend.document.parser.PdfDocumentParser;
import com.docpilot.backend.document.parser.TextDocumentParser;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.file.storage.FileContentReader;
import com.docpilot.backend.ai.service.RagIndexingTriggerService;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.rag.RagIndexingStatus;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private RagIndexingTriggerService ragIndexingTriggerService;

    private ParseTaskConsumeEntryServiceImpl buildService() {
        lenient().when(parseTaskConsumeRecordMapper.insertProcessing(anyString(), anyLong())).thenReturn(1);
        lenient().when(ragIndexingTriggerService.indexAfterParse(any(), any(), any(ParseResult.class)))
                .thenAnswer(invocation -> indexingSuccess(
                        invocation.getArgument(0, Long.class),
                        invocation.getArgument(1, Long.class)
                ));
        return new ParseTaskConsumeEntryServiceImpl(
                parseTaskMapper,
                documentMapper,
                fileRecordMapper,
                new ParserRegistry(List.of(
                        new TextDocumentParser(fileContentReader),
                        new PdfDocumentParser(fileContentReader),
                        new HtmlDocumentParser(fileContentReader),
                        new DocxDocumentParser(fileContentReader)
                )),
                parseTaskConsumeRecordMapper,
                stringRedisTemplate,
                ragIndexingTriggerService,
                20L * 1024L * 1024L,
                10_000L,
                300L
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
        verify(ragIndexingTriggerService, never()).indexAfterParse(any(), any(), any(ParseResult.class));
    }

    @Test
    void shouldSkipDuplicateProcessingConsumeRecordBeforeLeaseExpires() {
        ParseTaskConsumeEntryServiceImpl service = buildService();
        ParseTaskMessage message = new ParseTaskMessage();
        message.setMessageKey("parse-task:1:create:test");
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTaskConsumeRecord consumeRecord = new ParseTaskConsumeRecord();
        consumeRecord.setStatus("PROCESSING");
        when(parseTaskConsumeRecordMapper.insertProcessing("parse-task:1:create:test", 1L)).thenReturn(0);
        when(parseTaskConsumeRecordMapper.selectByMessageKey("parse-task:1:create:test")).thenReturn(consumeRecord);
        when(parseTaskConsumeRecordMapper.takeoverStaleProcessing(anyString(), any())).thenReturn(0);

        service.handle(message);

        verify(parseTaskMapper, never()).selectById(1L);
        verify(parseTaskConsumeRecordMapper, never()).markSuccess(anyString());
        verify(parseTaskConsumeRecordMapper, never()).markFailed(anyString(), anyString());
    }

    @Test
    void shouldTakeOverStaleProcessingConsumeRecord() {
        ParseTaskConsumeEntryServiceImpl service = buildService();
        ParseTaskMessage message = new ParseTaskMessage();
        message.setMessageKey("parse-task:1:create:test");
        message.setTaskId(1L);
        message.setDocumentId(2L);
        message.setFileRecordId(3L);

        ParseTaskConsumeRecord consumeRecord = new ParseTaskConsumeRecord();
        consumeRecord.setStatus("PROCESSING");
        when(parseTaskConsumeRecordMapper.insertProcessing("parse-task:1:create:test", 1L)).thenReturn(0);
        when(parseTaskConsumeRecordMapper.selectByMessageKey("parse-task:1:create:test")).thenReturn(consumeRecord);
        when(parseTaskConsumeRecordMapper.takeoverStaleProcessing(anyString(), any())).thenReturn(1);

        ParseTask terminalTask = new ParseTask();
        terminalTask.setId(1L);
        terminalTask.setStatus("SUCCESS");
        when(parseTaskMapper.selectById(1L)).thenReturn(terminalTask);

        service.handle(message);

        verify(parseTaskConsumeRecordMapper).takeoverStaleProcessing(anyString(), any());
        verify(parseTaskMapper).selectById(1L);
        verify(parseTaskConsumeRecordMapper).markSuccess("parse-task:1:create:test");
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
        verify(documentMapper, org.mockito.Mockito.times(7)).updateById(documentCaptor.capture());
        List<Document> docUpdates = documentCaptor.getAllValues();
        assertEquals("UPLOADED", docUpdates.get(0).getParseStatus());
        Document indexedDocument = docUpdates.get(docUpdates.size() - 2);
        assertEquals("INDEXING", indexedDocument.getParseStatus());
        assertEquals("DocPilot parse content test", indexedDocument.getContent());
        assertEquals("DocPilot parse content test", indexedDocument.getSummary());
        Document successDocument = docUpdates.get(docUpdates.size() - 1);
        assertEquals("SUCCESS", successDocument.getParseStatus());
        verify(stringRedisTemplate, org.mockito.Mockito.times(7))
                .delete(CommonConstants.buildDocumentDetailCacheKey(100L, 2L));
        ArgumentCaptor<ParseResult> parseResultCaptor = ArgumentCaptor.forClass(ParseResult.class);
        verify(ragIndexingTriggerService).indexAfterParse(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(2L),
                parseResultCaptor.capture()
        );
        assertEquals("DocPilot parse content test", parseResultCaptor.getValue().fullText());
    }

    @Test
    void shouldParsePdfAndTriggerRagIndexingWhenPdfMessageConsumed() throws Exception {
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
        fileRecord.setStoragePath("demo.pdf");
        when(fileRecordMapper.selectById(33L)).thenReturn(fileRecord);
        when(fileContentReader.readBytes(org.mockito.ArgumentMatchers.eq("demo.pdf"), anyLong()))
                .thenReturn(pdfBytes("DocPilot parser pdf first page", "DocPilot parser pdf second page"));

        service.handle(message);

        ArgumentCaptor<Document> updateCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper, org.mockito.Mockito.times(7)).updateById(updateCaptor.capture());
        Document indexedDocument = updateCaptor.getAllValues().get(updateCaptor.getAllValues().size() - 2);
        assertTrue(indexedDocument.getContent().contains("DocPilot parser pdf first page"));
        assertTrue(indexedDocument.getContent().contains("DocPilot parser pdf second page"));
        assertTrue(indexedDocument.getSummary().contains("DocPilot parser pdf first page"));
        assertEquals("SUCCESS", updateCaptor.getAllValues().get(updateCaptor.getAllValues().size() - 1).getParseStatus());
        ArgumentCaptor<ParseResult> parseResultCaptor = ArgumentCaptor.forClass(ParseResult.class);
        verify(ragIndexingTriggerService).indexAfterParse(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(22L),
                parseResultCaptor.capture()
        );
        assertTrue(parseResultCaptor.getValue().fullText().contains("DocPilot parser pdf first page"));
        assertTrue(parseResultCaptor.getValue().blocks().stream()
                .anyMatch(block -> Integer.valueOf(1).equals(block.pageNumber())));
    }

    @Test
    void shouldMarkFailedAndKeepParsedContentWhenRagIndexingThrows() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(12L);
        message.setDocumentId(23L);
        message.setFileRecordId(34L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(12L);
        parseTask.setDocumentId(23L);
        parseTask.setFileRecordId(34L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(12L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(23L);
        document.setUserId(100L);
        document.setFileRecordId(34L);
        when(documentMapper.selectById(23L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(34L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("sample.txt");
        fileRecord.setStoragePath("sample.txt");
        when(fileRecordMapper.selectById(34L)).thenReturn(fileRecord);
        when(fileContentReader.readText("sample.txt")).thenReturn("RAG trigger isolation content");
        doThrow(new IllegalStateException("provider endpoint should not leak"))
                .when(ragIndexingTriggerService)
                .indexAfterParse(
                        org.mockito.ArgumentMatchers.eq(100L),
                        org.mockito.ArgumentMatchers.eq(23L),
                        any(ParseResult.class)
                );

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(6)).updateById(taskCaptor.capture());
        ParseTask failedTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals("FAILED", failedTask.getStatus());
        assertTrue(failedTask.getErrorMsg().contains("RAG_INDEX_EXCEPTION"));
        assertTrue(!failedTask.getErrorMsg().contains("provider endpoint should not leak"));
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper, org.mockito.Mockito.times(7)).updateById(documentCaptor.capture());
        Document indexedDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 2);
        assertEquals("INDEXING", indexedDocument.getParseStatus());
        assertEquals("RAG trigger isolation content", indexedDocument.getContent());
        assertEquals("RAG trigger isolation content", indexedDocument.getSummary());
        assertEquals("FAILED", documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1).getParseStatus());
        verify(parseTaskConsumeRecordMapper).markSuccess("legacy-task:12");
    }

    @Test
    void shouldMarkFailedWhenRagIndexingReturnsFailure() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(13L);
        message.setDocumentId(24L);
        message.setFileRecordId(35L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(13L);
        parseTask.setDocumentId(24L);
        parseTask.setFileRecordId(35L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(13L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(24L);
        document.setUserId(100L);
        document.setFileRecordId(35L);
        when(documentMapper.selectById(24L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(35L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("failed-index.txt");
        fileRecord.setStoragePath("failed-index.txt");
        when(fileRecordMapper.selectById(35L)).thenReturn(fileRecord);
        when(fileContentReader.readText("failed-index.txt")).thenReturn("Parsed content retained after failed indexing");
        when(ragIndexingTriggerService.indexAfterParse(any(), any(), any(ParseResult.class)))
                .thenReturn(new RagIndexingResult(
                        RagIndexingStatus.FAILED,
                        24L,
                        100L,
                        1,
                        2,
                        0,
                        "provider detail Bearer secret SELECT * FROM tb_document document marker"
                ));

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(6)).updateById(taskCaptor.capture());
        ParseTask failedTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals("FAILED", failedTask.getStatus());
        assertTrue(failedTask.getErrorMsg().contains("RAG_INDEX_FAILED"));
        assertTrue(failedTask.getErrorMsg().contains("failureCode=UNKNOWN"));
        assertTrue(failedTask.getErrorMsg().contains("chunkCount=2"));
        assertTrue(failedTask.getErrorMsg().contains("preparedVectorCount=0"));
        assertTrue(failedTask.getErrorMsg().contains("indexVersion=1"));
        assertTrue(!failedTask.getErrorMsg().contains("provider detail"));
        assertTrue(!failedTask.getErrorMsg().contains("Bearer"));
        assertTrue(!failedTask.getErrorMsg().contains("SELECT"));
        assertTrue(!failedTask.getErrorMsg().contains("document marker"));
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper, org.mockito.Mockito.times(7)).updateById(documentCaptor.capture());
        Document indexedDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 2);
        assertEquals("Parsed content retained after failed indexing", indexedDocument.getContent());
        assertEquals("FAILED", documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1).getParseStatus());
    }

    @Test
    void shouldMarkFailedWhenRagIndexingResultDoesNotMatchRequest() {
        ParseTaskConsumeEntryServiceImpl service = buildService();

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(14L);
        message.setDocumentId(25L);
        message.setFileRecordId(36L);

        ParseTask parseTask = new ParseTask();
        parseTask.setId(14L);
        parseTask.setDocumentId(25L);
        parseTask.setFileRecordId(36L);
        parseTask.setStatus("PENDING");
        when(parseTaskMapper.selectById(14L)).thenReturn(parseTask);

        Document document = new Document();
        document.setId(25L);
        document.setUserId(100L);
        document.setFileRecordId(36L);
        when(documentMapper.selectById(25L)).thenReturn(document);

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(36L);
        fileRecord.setFileExt("txt");
        fileRecord.setFileName("mismatch-index.txt");
        fileRecord.setStoragePath("mismatch-index.txt");
        when(fileRecordMapper.selectById(36L)).thenReturn(fileRecord);
        when(fileContentReader.readText("mismatch-index.txt")).thenReturn("Parsed content with a mismatched index result");
        when(ragIndexingTriggerService.indexAfterParse(any(), any(), any(ParseResult.class)))
                .thenReturn(new RagIndexingResult(RagIndexingStatus.SUCCESS, 999L, 100L, 1, 1, 1, "indexed"));

        service.handle(message);

        ArgumentCaptor<ParseTask> taskCaptor = ArgumentCaptor.forClass(ParseTask.class);
        verify(parseTaskMapper, org.mockito.Mockito.times(6)).updateById(taskCaptor.capture());
        ParseTask failedTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals("FAILED", failedTask.getStatus());
        assertTrue(failedTask.getErrorMsg().contains("RAG_INDEX_RESULT_MISMATCH"));
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
        verify(ragIndexingTriggerService, never()).indexAfterParse(any(), any(), any(ParseResult.class));
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
        fileRecord.setFileExt("bin");
        fileRecord.setFileName("demo.bin");
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

    private byte[] pdfBytes(String... pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private RagIndexingResult indexingSuccess(Long userId, Long documentId) {
        return new RagIndexingResult(RagIndexingStatus.SUCCESS, documentId, userId, 1, 1, 1, "indexed");
    }
}

