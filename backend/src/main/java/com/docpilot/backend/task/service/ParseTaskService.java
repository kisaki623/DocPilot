package com.docpilot.backend.task.service;

import com.docpilot.backend.task.vo.ParseTaskCreateResponse;

public interface ParseTaskService {

    ParseTaskCreateResponse create(Long documentId, Long userId);

    ParseTaskCreateResponse retry(Long documentId, Long userId);

    ParseTaskCreateResponse reparse(Long documentId, Long userId);
}

