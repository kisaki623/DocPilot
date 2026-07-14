package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;

public interface KnowledgeBaseRagRetrievalService {

    KnowledgeBaseRagRetrievalResult retrieve(KnowledgeBaseRagRetrievalQuery query);
}
