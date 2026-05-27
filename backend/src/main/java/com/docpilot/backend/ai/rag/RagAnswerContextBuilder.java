package com.docpilot.backend.ai.rag;

import java.util.ArrayList;
import java.util.List;

public class RagAnswerContextBuilder {

    public RagAnswerContext build(List<VectorSearchResult> hits) {
        if (hits == null || hits.isEmpty()) {
            return new RagAnswerContext("", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<RagCitation> citations = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchResult hit = hits.get(i);
            DocumentChunk chunk = hit.chunk();
            context.append("[")
                    .append(i + 1)
                    .append("] documentId=")
                    .append(chunk.documentId())
                    .append(", chunkIndex=")
                    .append(chunk.chunkIndex())
                    .append(", score=")
                    .append(String.format(java.util.Locale.ROOT, "%.4f", hit.score()))
                    .append("\n")
                    .append(chunk.text().trim())
                    .append("\n\n");
            citations.add(new RagCitation(chunk.documentId(), chunk.chunkIndex(), hit.score(), chunk.metadata()));
        }
        return new RagAnswerContext(context.toString().trim(), citations);
    }
}
