package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;

public interface KnowledgeBaseRagQaService {

    KnowledgeBaseRagQaAnswer answer(KnowledgeBaseRagQaQuery query);
}
