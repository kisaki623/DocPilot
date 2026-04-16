package com.docpilot.backend.document.service;

import com.docpilot.backend.document.vo.DocumentCreateResponse;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.document.vo.DocumentListResponse;

public interface DocumentService {

    DocumentCreateResponse create(Long fileRecordId, Long userId);

    DocumentListResponse listByUser(Long userId, Integer pageNo, Integer pageSize);

    DocumentDetailResponse getDetailById(Long documentId, Long userId);
}

