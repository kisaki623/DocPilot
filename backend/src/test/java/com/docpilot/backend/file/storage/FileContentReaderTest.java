package com.docpilot.backend.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContentReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadLocalTextWithUtf8() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "DocPilot 本地读取测试");

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        String content = reader.readText(file.toString());

        assertEquals("DocPilot 本地读取测试", content);
    }

    @Test
    void shouldReadMinioTextWhenWriterAvailable() {
        MinioFileStorageWriter minioFileStorageWriter = mock(MinioFileStorageWriter.class);
        when(minioFileStorageWriter.readText("minio://docpilot/uploads/demo.txt"))
                .thenReturn("DocPilot MinIO 读取测试");

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(minioFileStorageWriter);
        FileContentReader reader = new FileContentReader(provider);

        String content = reader.readText("minio://docpilot/uploads/demo.txt");

        assertEquals("DocPilot MinIO 读取测试", content);
    }

    @Test
    void shouldFailClearlyWhenMinioPathButWriterMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reader.readText("minio://docpilot/uploads/demo.txt"));

        assertTrue(ex.getMessage().contains("未启用 MinIO 模式"));
    }
}


