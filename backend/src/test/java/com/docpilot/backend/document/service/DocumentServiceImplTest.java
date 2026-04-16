package com.docpilot.backend.document.service;

import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.document.service.impl.DocumentServiceImpl;
import com.docpilot.backend.document.vo.DocumentCreateResponse;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.document.vo.DocumentListItemResponse;
import com.docpilot.backend.document.vo.DocumentListResponse;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private FileRecordMapper fileRecordMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private DocumentServiceImpl buildService() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        return new DocumentServiceImpl(documentMapper, fileRecordMapper, stringRedisTemplate, objectMapper);
    }

    @Test
    void shouldCreateDocumentWhenFileRecordIsValid() {
        DocumentServiceImpl documentService = buildService();

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(11L);
        fileRecord.setUserId(100L);
        fileRecord.setFileName("guide.md");
        when(fileRecordMapper.selectById(11L)).thenReturn(fileRecord);

        doAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(21L);
            return 1;
        }).when(documentMapper).insert(any(Document.class));

        DocumentCreateResponse response = documentService.create(11L, 100L);

        assertEquals(21L, response.getId());
        assertEquals(100L, response.getUserId());
        assertEquals(11L, response.getFileRecordId());
        assertEquals("guide.md", response.getTitle());
        assertEquals("PENDING", response.getParseStatus());

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getParseStatus());
    }

    @Test
    void shouldThrowWhenFileRecordNotFound() {
        DocumentServiceImpl documentService = buildService();
        when(fileRecordMapper.selectById(11L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> documentService.create(11L, 100L));
    }

    @Test
    void shouldThrowWhenFileRecordBelongsToAnotherUser() {
        DocumentServiceImpl documentService = buildService();

        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(11L);
        fileRecord.setUserId(200L);
        when(fileRecordMapper.selectById(11L)).thenReturn(fileRecord);

        assertThrows(BusinessException.class, () -> documentService.create(11L, 100L));
    }

    @Test
    void shouldListDocumentsByCurrentUserWithPaging() {
        DocumentServiceImpl documentService = buildService();

        DocumentListItemResponse item = new DocumentListItemResponse();
        item.setDocumentId(101L);
        item.setFileRecordId(11L);
        item.setFileName("guide.md");
        item.setFileType("md");
        item.setParseStatus("SUCCESS");
        item.setSummary("summary");

        when(documentMapper.countUserDocuments(100L)).thenReturn(1L);
        when(documentMapper.selectUserDocumentPage(100L, 0, 10)).thenReturn(List.of(item));

        DocumentListResponse response = documentService.listByUser(100L, 1, 10);

        assertEquals(1, response.getPageNo());
        assertEquals(10, response.getPageSize());
        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getRecords().size());
        assertEquals(101L, response.getRecords().get(0).getDocumentId());
        assertEquals("guide.md", response.getRecords().get(0).getFileName());
        assertEquals("md", response.getRecords().get(0).getFileType());
        assertEquals(ParseStatusConstants.LABEL_SUCCESS, response.getRecords().get(0).getParseStatusLabel());

        verify(documentMapper).countUserDocuments(100L);
        verify(documentMapper).selectUserDocumentPage(100L, 0, 10);
    }

    @Test
    void shouldUseDefaultPageParamsWhenInvalid() {
        DocumentServiceImpl documentService = buildService();

        when(documentMapper.countUserDocuments(100L)).thenReturn(0L);

        DocumentListResponse response = documentService.listByUser(100L, -1, 999);

        assertEquals(CommonConstants.DEFAULT_PAGE_NUM, response.getPageNo());
        assertEquals(CommonConstants.MAX_PAGE_SIZE, response.getPageSize());
        assertEquals(0L, response.getTotal());
        assertEquals(0, response.getRecords().size());

        verify(documentMapper).countUserDocuments(100L);
        verify(documentMapper, never()).selectUserDocumentPage(eq(100L), any(Integer.class), any(Integer.class));
    }

    @Test
    void shouldGetDetailWhenDocumentBelongsToCurrentUser() {
        DocumentServiceImpl documentService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentDetailResponse detail = new DocumentDetailResponse();
        detail.setDocumentId(101L);
        detail.setFileRecordId(11L);
        detail.setTitle("guide.md");
        detail.setFileName("guide.md");
        detail.setFileType("md");
        detail.setParseStatus("SUCCESS");
        detail.setSummary("summary");
        detail.setContent("content");
        when(documentMapper.selectUserDocumentDetail(101L, 100L)).thenReturn(detail);

        DocumentDetailResponse response = documentService.getDetailById(101L, 100L);

        assertEquals(101L, response.getDocumentId());
        assertEquals("guide.md", response.getFileName());
        assertEquals("md", response.getFileType());
        assertEquals("SUCCESS", response.getParseStatus());
        assertEquals(ParseStatusConstants.LABEL_SUCCESS, response.getParseStatusLabel());
        assertEquals("content", response.getContent());
        verify(valueOperations).set(
                eq(CommonConstants.buildDocumentDetailCacheKey(100L, 101L)),
                anyString(),
                eq(CommonConstants.DOCUMENT_DETAIL_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldReturnDetailFromCacheWhenCacheHit() throws Exception {
        DocumentServiceImpl documentService = buildService();

        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(100L, 101L);
        DocumentDetailResponse cached = new DocumentDetailResponse();
        cached.setDocumentId(101L);
        cached.setFileRecordId(11L);
        cached.setTitle("guide.md");
        cached.setFileName("guide.md");
        cached.setFileType("md");
        cached.setParseStatus("SUCCESS");
        cached.setSummary("summary");
        cached.setContent("content");
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(cached));

        DocumentDetailResponse response = documentService.getDetailById(101L, 100L);

        assertEquals(101L, response.getDocumentId());
        assertEquals(ParseStatusConstants.LABEL_SUCCESS, response.getParseStatusLabel());
        verify(documentMapper, never()).selectById(101L);
        verify(documentMapper, never()).selectUserDocumentDetail(101L, 100L);
    }

    @Test
    void shouldBypassCacheWhenCachedStatusIsPending() throws Exception {
        DocumentServiceImpl documentService = buildService();

        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(100L, 101L);
        DocumentDetailResponse cached = new DocumentDetailResponse();
        cached.setDocumentId(101L);
        cached.setFileRecordId(11L);
        cached.setTitle("guide.md");
        cached.setFileName("guide.md");
        cached.setFileType("md");
        cached.setParseStatus("PENDING");
        cached.setSummary("old summary");
        cached.setContent("old content");
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(cached));

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentDetailResponse dbDetail = new DocumentDetailResponse();
        dbDetail.setDocumentId(101L);
        dbDetail.setFileRecordId(11L);
        dbDetail.setTitle("guide.md");
        dbDetail.setFileName("guide.md");
        dbDetail.setFileType("md");
        dbDetail.setParseStatus("SUCCESS");
        dbDetail.setSummary("new summary");
        dbDetail.setContent("new content");
        when(documentMapper.selectUserDocumentDetail(101L, 100L)).thenReturn(dbDetail);

        DocumentDetailResponse response = documentService.getDetailById(101L, 100L);

        assertEquals("SUCCESS", response.getParseStatus());
        assertEquals("new content", response.getContent());
        verify(stringRedisTemplate).delete(cacheKey);
        verify(documentMapper).selectById(101L);
        verify(documentMapper).selectUserDocumentDetail(101L, 100L);
    }

    @Test
    void shouldNotCacheDetailWhenStatusIsPending() {
        DocumentServiceImpl documentService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentDetailResponse detail = new DocumentDetailResponse();
        detail.setDocumentId(101L);
        detail.setFileRecordId(11L);
        detail.setTitle("guide.md");
        detail.setFileName("guide.md");
        detail.setFileType("md");
        detail.setParseStatus("PENDING");
        detail.setSummary("summary");
        detail.setContent("content");
        when(documentMapper.selectUserDocumentDetail(101L, 100L)).thenReturn(detail);

        DocumentDetailResponse response = documentService.getDetailById(101L, 100L);

        assertEquals("PENDING", response.getParseStatus());
        verify(valueOperations, never()).set(
                eq(CommonConstants.buildDocumentDetailCacheKey(100L, 101L)),
                anyString(),
                eq(CommonConstants.DOCUMENT_DETAIL_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldFallbackToDbWhenCachePayloadInvalid() {
        DocumentServiceImpl documentService = buildService();

        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(100L, 101L);
        when(valueOperations.get(cacheKey)).thenReturn("{invalid-json");

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentDetailResponse detail = new DocumentDetailResponse();
        detail.setDocumentId(101L);
        detail.setFileRecordId(11L);
        detail.setTitle("guide.md");
        detail.setFileName("guide.md");
        detail.setFileType("md");
        detail.setParseStatus("SUCCESS");
        detail.setSummary("summary");
        detail.setContent("content");
        when(documentMapper.selectUserDocumentDetail(101L, 100L)).thenReturn(detail);

        DocumentDetailResponse response = documentService.getDetailById(101L, 100L);

        assertEquals(101L, response.getDocumentId());
        verify(stringRedisTemplate).delete(cacheKey);
        verify(valueOperations, times(1)).set(
                eq(cacheKey),
                anyString(),
                eq(CommonConstants.DOCUMENT_DETAIL_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldUseUnknownLabelWhenParseStatusIsNotSupported() {
        DocumentServiceImpl documentService = buildService();

        DocumentListItemResponse item = new DocumentListItemResponse();
        item.setDocumentId(101L);
        item.setParseStatus("CUSTOM_STATUS");

        when(documentMapper.countUserDocuments(100L)).thenReturn(1L);
        when(documentMapper.selectUserDocumentPage(100L, 0, 10)).thenReturn(List.of(item));

        DocumentListResponse response = documentService.listByUser(100L, 1, 10);

        assertEquals(1, response.getRecords().size());
        assertEquals(ParseStatusConstants.LABEL_UNKNOWN, response.getRecords().get(0).getParseStatusLabel());
    }

    @Test
    void shouldThrowWhenDetailDocumentNotFound() {
        DocumentServiceImpl documentService = buildService();
        when(documentMapper.selectById(101L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> documentService.getDetailById(101L, 100L));
    }

    @Test
    void shouldThrowWhenDetailDocumentBelongsToAnotherUser() {
        DocumentServiceImpl documentService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(200L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        assertThrows(BusinessException.class, () -> documentService.getDetailById(101L, 100L));
    }

    @Test
    void shouldThrowWhenRelatedFileRecordMissingInDetail() {
        DocumentServiceImpl documentService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentDetailResponse detail = new DocumentDetailResponse();
        detail.setDocumentId(101L);
        detail.setFileRecordId(11L);
        detail.setParseStatus("SUCCESS");
        detail.setSummary("summary");
        detail.setContent("content");
        when(documentMapper.selectUserDocumentDetail(101L, 100L)).thenReturn(detail);

        assertThrows(BusinessException.class, () -> documentService.getDetailById(101L, 100L));
    }
}

