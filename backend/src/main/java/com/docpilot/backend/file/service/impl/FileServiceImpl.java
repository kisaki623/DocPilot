package com.docpilot.backend.file.service.impl;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.file.dto.ChunkUploadInitRequest;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.file.service.FileService;
import com.docpilot.backend.file.storage.FileStorageWriter;
import com.docpilot.backend.file.vo.ChunkUploadInitResponse;
import com.docpilot.backend.file.vo.ChunkUploadStatusResponse;
import com.docpilot.backend.file.vo.FileUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class FileServiceImpl implements FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "md", "txt");
    private static final String META_FIELD_USER_ID = "userId";
    private static final String META_FIELD_FILE_NAME = "fileName";
    private static final String META_FIELD_FILE_EXT = "fileExt";
    private static final String META_FIELD_CONTENT_TYPE = "contentType";
    private static final String META_FIELD_FILE_SIZE = "fileSize";
    private static final String META_FIELD_CHUNK_SIZE = "chunkSize";
    private static final String META_FIELD_TOTAL_CHUNKS = "totalChunks";
    private static final String META_FIELD_FILE_HASH = "fileHash";
    private static final String META_FIELD_STATUS = "status";

    private final FileRecordMapper fileRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileStorageWriter fileStorageWriter;
    private final String chunkTempDir;
    private final long chunkSessionTtlSeconds;

    public FileServiceImpl(FileRecordMapper fileRecordMapper,
                           StringRedisTemplate stringRedisTemplate,
                           FileStorageWriter fileStorageWriter,
                           @Value("${app.file.storage.chunk-temp-dir:./data/upload-chunks}") String chunkTempDir,
                           @Value("${app.file.storage.chunk-session-ttl-seconds:86400}") long chunkSessionTtlSeconds) {
        this.fileRecordMapper = fileRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.fileStorageWriter = fileStorageWriter;
        this.chunkTempDir = chunkTempDir;
        this.chunkSessionTtlSeconds = chunkSessionTtlSeconds;
    }

    @Override
    public FileUploadResponse upload(MultipartFile file, Long userId) {
        ValidationUtils.requireNonNull(file, "file");
        ValidationUtils.requireNonNull(userId, "userId");
        checkUploadRateLimit(userId);
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String extension = extractAndValidateExtension(originalName);
        String fileHash = calculateSha256(file);

        return persistUploadedFile(
                userId,
                originalName,
                extension,
                file.getContentType(),
                file.getSize(),
                fileHash,
                () -> fileStorageWriter.store(file, extension)
        );
    }

    @Override
    public ChunkUploadInitResponse initChunkUpload(ChunkUploadInitRequest request, Long userId) {
        ValidationUtils.requireNonNull(request, "request");
        ValidationUtils.requireNonNull(userId, "userId");
        checkUploadRateLimit(userId);

        String originalName = normalizeOriginalName(request.getFileName());
        String extension = extractAndValidateExtension(originalName);
        String fileHash = normalizeHash(request.getFileHash());
        long fileSize = request.getFileSize();

        String sessionIndexKey = CommonConstants.buildChunkUploadSessionIndexKey(userId, fileHash, fileSize);
        String cachedUploadId = stringRedisTemplate.opsForValue().get(sessionIndexKey);
        if (cachedUploadId != null && !cachedUploadId.isBlank()) {
            ChunkSessionMeta cachedMeta = loadChunkMeta(cachedUploadId);
            if (cachedMeta != null && userId.equals(cachedMeta.userId())) {
                refreshChunkSessionTtl(cachedUploadId, sessionIndexKey);
                return toChunkInitResponse(cachedUploadId, cachedMeta);
            }
            stringRedisTemplate.delete(sessionIndexKey);
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String metaKey = CommonConstants.buildChunkUploadMetaKey(uploadId);

        Map<String, String> meta = Map.of(
                META_FIELD_USER_ID, String.valueOf(userId),
                META_FIELD_FILE_NAME, originalName,
                META_FIELD_FILE_EXT, extension,
                META_FIELD_CONTENT_TYPE, request.getContentType() == null ? "application/octet-stream" : request.getContentType(),
                META_FIELD_FILE_SIZE, String.valueOf(request.getFileSize()),
                META_FIELD_CHUNK_SIZE, String.valueOf(request.getChunkSize()),
                META_FIELD_TOTAL_CHUNKS, String.valueOf(request.getTotalChunks()),
                META_FIELD_FILE_HASH, fileHash,
                META_FIELD_STATUS, "INIT"
        );
        stringRedisTemplate.opsForHash().putAll(metaKey, meta);
        refreshChunkSessionTtl(uploadId, sessionIndexKey);
        stringRedisTemplate.opsForValue().set(sessionIndexKey, uploadId, chunkSessionTtlSeconds, TimeUnit.SECONDS);

        ChunkUploadInitResponse response = new ChunkUploadInitResponse();
        response.setUploadId(uploadId);
        response.setTotalChunks(request.getTotalChunks());
        return response;
    }

    @Override
    public ChunkUploadStatusResponse uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk, Long userId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ChunkSessionMeta meta = requireChunkMeta(uploadId, userId);

        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= meta.totalChunks()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片序号非法");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片内容不能为空");
        }

        Path chunkDirectory = Paths.get(chunkTempDir, uploadId).toAbsolutePath().normalize();
        Path chunkFile = chunkDirectory.resolve(chunkIndex + ".part").normalize();
        try {
            Files.createDirectories(chunkDirectory);
            try (InputStream inputStream = chunk.getInputStream()) {
                Files.copy(inputStream, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "分片写入失败");
        }

        String uploadedSetKey = CommonConstants.buildChunkUploadChunksKey(uploadId);
        stringRedisTemplate.opsForSet().add(uploadedSetKey, String.valueOf(chunkIndex));
        stringRedisTemplate.opsForHash().put(CommonConstants.buildChunkUploadMetaKey(uploadId), META_FIELD_STATUS, "UPLOADING");
        refreshChunkSessionTtl(uploadId,
                CommonConstants.buildChunkUploadSessionIndexKey(userId, meta.fileHash(), meta.fileSize()));

        return toChunkStatusResponse(uploadId, meta);
    }

    @Override
    public ChunkUploadStatusResponse getChunkUploadStatus(String uploadId, Long userId) {
        ChunkSessionMeta meta = requireChunkMeta(uploadId, userId);
        refreshChunkSessionTtl(uploadId,
                CommonConstants.buildChunkUploadSessionIndexKey(userId, meta.fileHash(), meta.fileSize()));
        return toChunkStatusResponse(uploadId, meta);
    }

    @Override
    public FileUploadResponse completeChunkUpload(String uploadId, Long userId) {
        ChunkSessionMeta meta = requireChunkMeta(uploadId, userId);
        List<Integer> uploadedChunkIndexes = listUploadedChunkIndexes(uploadId);
        if (uploadedChunkIndexes.size() != meta.totalChunks()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "分片未全部上传，当前已上传 " + uploadedChunkIndexes.size() + "/" + meta.totalChunks()
            );
        }

        Path chunkDirectory = Paths.get(chunkTempDir, uploadId).toAbsolutePath().normalize();
        Path mergedFile = chunkDirectory.resolve("merged." + meta.fileExt()).normalize();
        try {
            mergeChunks(chunkDirectory, mergedFile, meta.totalChunks());

            long mergedFileSize = Files.size(mergedFile);
            if (mergedFileSize != meta.fileSize()) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "分片文件大小校验失败，请重新上传");
            }

            String calculatedHash = calculateSha256(mergedFile);
            if (!calculatedHash.equals(meta.fileHash())) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "分片文件哈希校验失败，请重新上传");
            }

            FileUploadResponse response = persistUploadedFile(
                    userId,
                    meta.fileName(),
                    meta.fileExt(),
                    meta.contentType(),
                    meta.fileSize(),
                    calculatedHash,
                    () -> fileStorageWriter.store(mergedFile, meta.fileExt())
            );

            clearChunkSession(uploadId, userId, meta.fileHash(), meta.fileSize());
            deleteChunkDirectory(chunkDirectory);
            return response;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "读取分片合并文件失败");
        } finally {
            deleteFileQuietly(mergedFile);
        }
    }

    private String calculateSha256(MultipartFile file) {
        MessageDigest digest = createSha256Digest();
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "读取上传文件失败");
        }
    }

    private String calculateSha256(Path filePath) {
        MessageDigest digest = createSha256Digest();
        try (InputStream inputStream = Files.newInputStream(filePath);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInputStream.read(buffer) != -1) {
                // Read full stream for digest.
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "读取分片合并文件失败");
        }
    }

    private String normalizeOriginalName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "unknown.txt";
        }
        return Paths.get(originalName).getFileName().toString();
    }

    private String extractAndValidateExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件扩展名不能为空");
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "仅支持 pdf、md、txt 文件");
        }
        return extension;
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件哈希算法不可用");
        }
    }

    private FileUploadResponse persistUploadedFile(Long userId,
                                                   String originalName,
                                                   String extension,
                                                   String contentType,
                                                   Long fileSize,
                                                   String fileHash,
                                                   Supplier<String> storagePathSupplier) {
        FileRecord existingRecord = fileRecordMapper.selectLatestByUserAndHashAndSize(userId, fileHash, fileSize);
        if (existingRecord != null) {
            return toResponse(existingRecord, true);
        }

        String storedPath = storagePathSupplier.get();

        FileRecord record = new FileRecord();
        record.setUserId(userId);
        record.setFileName(originalName);
        record.setFileExt(extension);
        record.setContentType(contentType);
        record.setFileSize(fileSize);
        record.setStoragePath(storedPath);
        record.setFileHash(fileHash);

        try {
            fileRecordMapper.insert(record);
        } catch (RuntimeException ex) {
            fileStorageWriter.deleteQuietly(storedPath);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "保存文件记录失败");
        }

        return toResponse(record, false);
    }

    private void mergeChunks(Path chunkDirectory, Path mergedFile, int totalChunks) {
        try {
            Files.deleteIfExists(mergedFile);
            for (int index = 0; index < totalChunks; index++) {
                Path chunkFile = chunkDirectory.resolve(index + ".part");
                if (!Files.exists(chunkFile)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少分片: " + index);
                }
                byte[] chunkBytes = Files.readAllBytes(chunkFile);
                Files.write(mergedFile, chunkBytes,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "分片合并失败");
        }
    }

    private ChunkSessionMeta requireChunkMeta(String uploadId, Long userId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "uploadId 不能为空");
        }
        ChunkSessionMeta meta = loadChunkMeta(uploadId.trim());
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分片上传会话不存在或已过期");
        }
        if (!userId.equals(meta.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该分片上传会话");
        }
        validateChunkSessionMeta(meta);
        return meta;
    }

    private void validateChunkSessionMeta(ChunkSessionMeta meta) {
        if (meta.totalChunks() == null || meta.totalChunks() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片上传会话数据异常，请重新初始化上传");
        }
        if (meta.fileSize() == null || meta.fileSize() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片上传会话数据异常，请重新初始化上传");
        }
        if (meta.fileHash() == null || meta.fileHash().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片上传会话数据异常，请重新初始化上传");
        }
        if (meta.fileExt() == null || !ALLOWED_EXTENSIONS.contains(meta.fileExt())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片上传会话文件类型异常，请重新初始化上传");
        }
    }

    private ChunkSessionMeta loadChunkMeta(String uploadId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(CommonConstants.buildChunkUploadMetaKey(uploadId));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        try {
            return new ChunkSessionMeta(
                    Long.valueOf(String.valueOf(entries.get(META_FIELD_USER_ID))),
                    String.valueOf(entries.get(META_FIELD_FILE_NAME)),
                    String.valueOf(entries.get(META_FIELD_FILE_EXT)),
                    String.valueOf(entries.getOrDefault(META_FIELD_CONTENT_TYPE, "application/octet-stream")),
                    Long.valueOf(String.valueOf(entries.get(META_FIELD_FILE_SIZE))),
                    Integer.valueOf(String.valueOf(entries.get(META_FIELD_CHUNK_SIZE))),
                    Integer.valueOf(String.valueOf(entries.get(META_FIELD_TOTAL_CHUNKS))),
                    String.valueOf(entries.get(META_FIELD_FILE_HASH)),
                    String.valueOf(entries.getOrDefault(META_FIELD_STATUS, "INIT"))
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private ChunkUploadInitResponse toChunkInitResponse(String uploadId, ChunkSessionMeta meta) {
        ChunkUploadInitResponse response = new ChunkUploadInitResponse();
        response.setUploadId(uploadId);
        response.setTotalChunks(meta.totalChunks());
        response.setUploadedChunkIndexes(listUploadedChunkIndexes(uploadId));
        return response;
    }

    private ChunkUploadStatusResponse toChunkStatusResponse(String uploadId, ChunkSessionMeta meta) {
        List<Integer> uploadedIndexes = listUploadedChunkIndexes(uploadId);
        ChunkUploadStatusResponse response = new ChunkUploadStatusResponse();
        response.setUploadId(uploadId);
        response.setTotalChunks(meta.totalChunks());
        response.setUploadedCount(uploadedIndexes.size());
        response.setUploadedChunkIndexes(uploadedIndexes);
        return response;
    }

    private List<Integer> listUploadedChunkIndexes(String uploadId) {
        Set<String> uploadedSet = stringRedisTemplate.opsForSet().members(CommonConstants.buildChunkUploadChunksKey(uploadId));
        if (uploadedSet == null || uploadedSet.isEmpty()) {
            return List.of();
        }
        return uploadedSet.stream()
                .map(Integer::valueOf)
                .sorted()
                .collect(Collectors.toList());
    }

    private String normalizeHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fileHash 不能为空");
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private void refreshChunkSessionTtl(String uploadId, String sessionIndexKey) {
        stringRedisTemplate.expire(CommonConstants.buildChunkUploadMetaKey(uploadId), chunkSessionTtlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(CommonConstants.buildChunkUploadChunksKey(uploadId), chunkSessionTtlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(sessionIndexKey, chunkSessionTtlSeconds, TimeUnit.SECONDS);
    }

    private void clearChunkSession(String uploadId, Long userId, String fileHash, Long fileSize) {
        stringRedisTemplate.delete(CommonConstants.buildChunkUploadMetaKey(uploadId));
        stringRedisTemplate.delete(CommonConstants.buildChunkUploadChunksKey(uploadId));
        stringRedisTemplate.delete(CommonConstants.buildChunkUploadSessionIndexKey(userId, fileHash, fileSize));
    }

    private void deleteChunkDirectory(Path chunkDirectory) {
        if (chunkDirectory == null || !Files.exists(chunkDirectory)) {
            return;
        }
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory)) {
                for (Path path : stream) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(chunkDirectory);
        } catch (IOException ignored) {
            // Best effort cleanup only.
        }
    }

    private void deleteFileQuietly(Path filePath) {
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Best effort cleanup only.
        }
    }

    private FileUploadResponse toResponse(FileRecord record, boolean reused) {
        FileUploadResponse response = new FileUploadResponse();
        response.setId(record.getId());
        response.setUserId(record.getUserId());
        response.setFileName(record.getFileName());
        response.setFileExt(record.getFileExt());
        response.setContentType(record.getContentType());
        response.setFileSize(record.getFileSize());
        response.setStoragePath(record.getStoragePath());
        response.setReused(reused);
        return response;
    }

    private void checkUploadRateLimit(Long userId) {
        String key = CommonConstants.buildFileUploadRateLimitKey(userId);
        Long currentCount = stringRedisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            return;
        }
        if (currentCount == 1L) {
            stringRedisTemplate.expire(key, CommonConstants.FILE_UPLOAD_RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
            return;
        }
        if (currentCount > CommonConstants.FILE_UPLOAD_RATE_LIMIT_MAX_REQUESTS) {
            DocPilotMetrics.recordRateLimitTrigger("file_upload");
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "上传请求过于频繁，请稍后再试");
        }
    }

    private record ChunkSessionMeta(Long userId,
                                    String fileName,
                                    String fileExt,
                                    String contentType,
                                    Long fileSize,
                                    Integer chunkSize,
                                    Integer totalChunks,
                                    String fileHash,
                                    String status) {
    }
}

