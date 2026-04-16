package com.docpilot.backend.document.service.impl;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.document.service.DocumentService;
import com.docpilot.backend.document.vo.DocumentCreateResponse;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.document.vo.DocumentListItemResponse;
import com.docpilot.backend.document.vo.DocumentListResponse;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentMapper documentMapper;
    private final FileRecordMapper fileRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               FileRecordMapper fileRecordMapper,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentCreateResponse create(Long fileRecordId, Long userId) {
        ValidationUtils.requireNonNull(fileRecordId, "fileRecordId");
        ValidationUtils.requireNonNull(userId, "userId");

        FileRecord fileRecord = fileRecordMapper.selectById(fileRecordId);
        if (fileRecord == null) {
            throw new BusinessException(ErrorCode.FILE_RECORD_NOT_FOUND, "文件记录不存在");
        }
        if (!userId.equals(fileRecord.getUserId())) {
            throw new BusinessException(ErrorCode.FILE_RECORD_FORBIDDEN, "当前用户无权访问该文件记录");
        }

        Document existingDocument = documentMapper.selectLatestByUserAndFileRecordId(userId, fileRecordId);
        if (existingDocument != null) {
            return toResponse(existingDocument, true);
        }

        Document document = new Document();
        document.setUserId(userId);
        document.setFileRecordId(fileRecordId);
        document.setTitle(fileRecord.getFileName());
        document.setParseStatus(ParseStatusConstants.PENDING);

        try {
            documentMapper.insert(document);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.DOCUMENT_CREATE_FAILED, "文档创建失败");
        }

        return toResponse(document, false);
    }

    @Override
    public DocumentListResponse listByUser(Long userId, Integer pageNo, Integer pageSize) {
        ValidationUtils.requireNonNull(userId, "userId");

        int resolvedPageNo = resolvePageNo(pageNo);
        int resolvedPageSize = resolvePageSize(pageSize);
        int offset = (resolvedPageNo - 1) * resolvedPageSize;

        Long total = documentMapper.countUserDocuments(userId);
        if (total == null) {
            total = 0L;
        }

        List<DocumentListItemResponse> records = Collections.emptyList();
        if (total > 0) {
            records = documentMapper.selectUserDocumentPage(userId, offset, resolvedPageSize);
            records.forEach(this::fillParseStatusLabel);
        }

        DocumentListResponse response = new DocumentListResponse();
        response.setPageNo(resolvedPageNo);
        response.setPageSize(resolvedPageSize);
        response.setTotal(total);
        response.setRecords(records);
        return response;
    }

    @Override
    public DocumentDetailResponse getDetailById(Long documentId, Long userId) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonNull(userId, "userId");

        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(userId, documentId);
        DocumentDetailResponse cachedDetail = getDetailFromCache(cacheKey);
        if (cachedDetail != null && ParseStatusConstants.isTerminal(cachedDetail.getParseStatus())) {
            DocPilotMetrics.recordCacheAccess("document_detail", "hit");
            fillParseStatusLabel(cachedDetail);
            return cachedDetail;
        }
        if (cachedDetail != null) {
            // Parsing statuses are short-lived and easy to become stale; force DB read for fresh status.
            deleteCacheQuietly(cacheKey);
        }
        DocPilotMetrics.recordCacheAccess("document_detail", "miss");

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN, "当前用户无权访问该文档");
        }

        DocumentDetailResponse detail = documentMapper.selectUserDocumentDetail(documentId, userId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (detail.getFileName() == null || detail.getFileType() == null) {
            throw new BusinessException(ErrorCode.FILE_RECORD_NOT_FOUND, "关联文件记录不存在");
        }
        fillParseStatusLabel(detail);
        cacheDetail(cacheKey, detail);
        return detail;
    }

    private DocumentDetailResponse getDetailFromCache(String cacheKey) {
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson == null || cachedJson.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cachedJson, DocumentDetailResponse.class);
        } catch (JsonProcessingException ex) {
            deleteCacheQuietly(cacheKey);
            log.warn("文档详情缓存格式异常，已删除坏缓存并回源数据库。cacheKey={}, error={}", cacheKey, ex.getOriginalMessage());
            return null;
        } catch (Exception ex) {
            deleteCacheQuietly(cacheKey);
            log.warn("文档详情缓存读取失败，降级回源数据库。cacheKey={}", cacheKey, ex);
            return null;
        }
    }

    private void deleteCacheQuietly(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception deleteEx) {
            log.warn("文档详情坏缓存删除失败，cacheKey={}", cacheKey, deleteEx);
        }
    }

    private void cacheDetail(String cacheKey, DocumentDetailResponse detail) {
        if (!ParseStatusConstants.isTerminal(detail.getParseStatus())) {
            return;
        }
        try {
            String cacheValue = objectMapper.writeValueAsString(detail);
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    cacheValue,
                    CommonConstants.DOCUMENT_DETAIL_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (JsonProcessingException ex) {
            log.warn("文档详情缓存序列化失败，跳过写缓存。cacheKey={}", cacheKey, ex);
        } catch (Exception ex) {
            log.warn("文档详情写缓存失败，跳过缓存。cacheKey={}", cacheKey, ex);
        }
    }

    private void fillParseStatusLabel(DocumentListItemResponse item) {
        item.setParseStatusLabel(ParseStatusConstants.toLabel(item.getParseStatus()));
        item.setParseStatusDescription(ParseStatusConstants.toStageDescription(item.getParseStatus()));
    }

    private void fillParseStatusLabel(DocumentDetailResponse detail) {
        detail.setParseStatusLabel(ParseStatusConstants.toLabel(detail.getParseStatus()));
        detail.setParseStatusDescription(ParseStatusConstants.toStageDescription(detail.getParseStatus()));
    }

    private DocumentCreateResponse toResponse(Document document, boolean reused) {
        DocumentCreateResponse response = new DocumentCreateResponse();
        response.setId(document.getId());
        response.setUserId(document.getUserId());
        response.setFileRecordId(document.getFileRecordId());
        response.setTitle(document.getTitle());
        response.setParseStatus(document.getParseStatus());
        response.setReused(reused);
        return response;
    }

    private int resolvePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return CommonConstants.DEFAULT_PAGE_NUM;
        }
        return pageNo;
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return CommonConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, CommonConstants.MAX_PAGE_SIZE);
    }
}

