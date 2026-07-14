package com.docpilot.backend.knowledge.dto;

import java.util.List;

public class KnowledgeBaseAddDocumentsRequest {

    private List<Long> documentIds;

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }
}
