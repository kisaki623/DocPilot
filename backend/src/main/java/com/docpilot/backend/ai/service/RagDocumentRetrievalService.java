package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;

public interface RagDocumentRetrievalService {

    RagRetrievalResult retrieve(RagRetrievalQuery query);
}
