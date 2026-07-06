package com.docpilot.backend.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        when(minioFileStorageWriter.readBytes("minio://docpilot/uploads/demo.txt", Long.MAX_VALUE))
                .thenReturn("DocPilot MinIO 读取测试".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(minioFileStorageWriter);
        FileContentReader reader = new FileContentReader(provider);

        String content = reader.readText("minio://docpilot/uploads/demo.txt");

        assertEquals("DocPilot MinIO 读取测试", content);
    }

    @Test
    void shouldReadLocalBytesWithLimit() throws Exception {
        Path file = tempDir.resolve("sample.bin");
        Files.write(file, new byte[]{1, 2, 3});

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        byte[] bytes = reader.readBytes(file.toString(), 10);

        assertEquals(3, bytes.length);
        assertEquals(2, bytes[1]);
    }

    @Test
    void shouldRejectLocalFileOverLimit() throws Exception {
        Path file = tempDir.resolve("sample.bin");
        Files.write(file, new byte[]{1, 2, 3});

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reader.readBytes(file.toString(), 2));

        assertTrue(ex.getMessage().contains("exceeds parser size limit"));
    }

    @Test
    void shouldOpenLocalStream() throws Exception {
        Path file = tempDir.resolve("stream.txt");
        Files.writeString(file, "stream content");

        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        try (InputStream stream = reader.openStream(file.toString())) {
            assertEquals("stream content", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldFailClearlyWhenMinioPathButWriterMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MinioFileStorageWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FileContentReader reader = new FileContentReader(provider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reader.readText("minio://docpilot/uploads/demo.txt"));

        assertTrue(ex.getMessage().contains("MinIO storage is not enabled"));
    }
}


