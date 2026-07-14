package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;

public interface RagIndexingService {

    RagIndexingResult index(RagIndexingRequest request);

    RagIndexingResult rebuild(RagIndexingRequest request);

    RagIndexingResult retry(RagIndexingRequest request);
}
