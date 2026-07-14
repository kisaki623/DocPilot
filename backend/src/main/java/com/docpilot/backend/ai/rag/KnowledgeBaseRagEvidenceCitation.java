package com.docpilot.backend.ai.rag;

public record KnowledgeBaseRagEvidenceCitation(
        int index,
        Long knowledgeBaseId,
        Long documentId,
        String documentTitle,
        Integer indexVersion,
        Long chunkId,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        String contentHash,
        String snippet,
        String quoteText,
        Integer quoteStartOffset,
        Integer quoteEndOffset,
        String sectionPath,
        String structureType,
        Integer pageNumber,
        String sourceLocator,
        String blockType,
        double score,
        Double vectorScore,
        Double keywordScore,
        Double fusedScore,
        Double rerankScore
) {

    public KnowledgeBaseRagEvidenceCitation {
        if (index <= 0) {
            throw new IllegalArgumentException("index must be positive");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        documentTitle = documentTitle == null ? "" : documentTitle.trim();
        contentHash = contentHash == null ? "" : contentHash.trim();
        snippet = snippet == null ? "" : snippet.trim();
        quoteText = quoteText == null ? "" : quoteText.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        structureType = structureType == null ? "" : structureType.trim();
        sourceLocator = sourceLocator == null ? "" : sourceLocator.trim();
        blockType = blockType == null ? "" : blockType.trim();
        vectorScore = finiteOrNull(vectorScore);
        keywordScore = finiteOrNull(keywordScore);
        fusedScore = finiteOrNull(fusedScore);
        rerankScore = finiteOrNull(rerankScore);
    }

    private static Double finiteOrNull(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }
}
