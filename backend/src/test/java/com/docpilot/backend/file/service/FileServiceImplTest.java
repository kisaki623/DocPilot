package com.docpilot.backend.file.service;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.file.dto.ChunkUploadInitRequest;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.file.service.impl.FileServiceImpl;
import com.docpilot.backend.file.storage.FileStorageWriter;
import com.docpilot.backend.file.vo.ChunkUploadInitResponse;
import com.docpilot.backend.file.vo.ChunkUploadStatusResponse;
import com.docpilot.backend.file.vo.FileUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileRecordMapper fileRecordMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private FileStorageWriter fileStorageWriter;

    @TempDir
    Path tempDir;

    private FileServiceImpl buildService() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
        return new FileServiceImpl(fileRecordMapper, stringRedisTemplate, fileStorageWriter, tempDir.toString(), 3600L);
    }

    @Test
    void shouldUploadFileAndPersistRecord() {
        FileServiceImpl fileService = buildService();
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "hello docpilot".getBytes());

        doAnswer(invocation -> {
            FileRecord record = invocation.getArgument(0);
            record.setId(1L);
            return 1;
        }).when(fileRecordMapper).insert(any(FileRecord.class));
        Path storedPath = tempDir.resolve("stored.md");
        when(fileStorageWriter.store(any(MockMultipartFile.class), anyString())).thenReturn(storedPath.toString());

        FileUploadResponse response = fileService.upload(file, 100L);

        assertEquals(1L, response.getId());
        assertEquals(100L, response.getUserId());
        assertEquals("notes.md", response.getFileName());
        assertEquals("md", response.getFileExt());
        assertEquals((Long) 14L, response.getFileSize());
        assertEquals(storedPath.toString(), response.getStoragePath());

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRecordMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals("md", captor.getValue().getFileExt());
    }

    @Test
    void shouldRejectWhenUploadRateLimitExceeded() {
        FileServiceImpl fileService = buildService();
        when(valueOperations.increment(CommonConstants.buildFileUploadRateLimitKey(100L)))
                .thenReturn((long) CommonConstants.FILE_UPLOAD_RATE_LIMIT_MAX_REQUESTS + 1);
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "hello".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.upload(file, 100L));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals("上传请求过于频繁，请稍后再试", ex.getMessage());
    }

    @Test
    void shouldInitChunkUploadAndReturnUploadId() {
        FileServiceImpl fileService = buildService();
        when(valueOperations.get(anyString())).thenReturn(null);

        ChunkUploadInitRequest request = new ChunkUploadInitRequest();
        request.setFileName("demo.txt");
        request.setFileSize(11L);
        request.setChunkSize(6);
        request.setTotalChunks(2);
        request.setFileHash("b94d27b9934d3e08a52e52d7da7dabfade4f4f6f8f95f3f3f8f8f8f8f8f8f8f");

        ChunkUploadInitResponse response = fileService.initChunkUpload(request, 100L);

        assertEquals(2, response.getTotalChunks());
        verify(hashOperations).putAll(anyString(), any(Map.class));
        verify(stringRedisTemplate, atLeast(3)).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldReturnChunkStatusWithUploadedIndexes() {
        FileServiceImpl fileService = buildService();
        String uploadId = "u123";
        when(hashOperations.entries(CommonConstants.buildChunkUploadMetaKey(uploadId))).thenReturn(Map.of(
                "userId", "100",
                "fileName", "demo.txt",
                "fileExt", "txt",
                "contentType", "text/plain",
                "fileSize", "11",
                "chunkSize", "6",
                "totalChunks", "2",
                "fileHash", "abc",
                "status", "UPLOADING"
        ));
        when(setOperations.members(CommonConstants.buildChunkUploadChunksKey(uploadId))).thenReturn(Set.of("0", "1"));

        ChunkUploadStatusResponse response = fileService.getChunkUploadStatus(uploadId, 100L);

        assertEquals(2, response.getUploadedCount());
        assertEquals(2, response.getUploadedChunkIndexes().size());
    }

    @Test
    void shouldRejectChunkStatusAccessFromAnotherUser() {
        FileServiceImpl fileService = buildService();
        String uploadId = "u999";
        when(hashOperations.entries(CommonConstants.buildChunkUploadMetaKey(uploadId))).thenReturn(Map.of(
                "userId", "100",
                "fileName", "demo.txt",
                "fileExt", "txt",
                "contentType", "text/plain",
                "fileSize", "11",
                "chunkSize", "6",
                "totalChunks", "2",
                "fileHash", "abc",
                "status", "UPLOADING"
        ));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getChunkUploadStatus(uploadId, 101L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("无权访问"));
    }

    @Test
    void shouldRejectExpiredChunkSessionClearly() {
        FileServiceImpl fileService = buildService();
        when(hashOperations.entries(CommonConstants.buildChunkUploadMetaKey("expired"))).thenReturn(Map.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getChunkUploadStatus("expired", 100L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("已过期"));
    }
}


