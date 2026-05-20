package com.docpilot.backend.ai.rag;

import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagQaContextBuilder {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final RagEmbeddingProperties embeddingProperties;

    public RagQaContextBuilder() {
        this(new EmbeddingModelFactory(), new RagEmbeddingProperties());
    }

    @Autowired
    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory, RagEmbeddingProperties embeddingProperties) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.embeddingProperties = embeddingProperties;
    }

    public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonBlank(question, "question");
        if (documentText == null || documentText.isBlank()) {
            return RagQaContext.empty();
        }
        int resolvedTopK = Math.max(1, topK);
        int resolvedMaxContextChars = Math.max(1, maxContextChars);

        EmbeddingModel embeddingModel = embeddingModelFactory.create(embeddingProperties);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        RagIndexService indexService = new RagIndexService(embeddingModel, vectorStore);
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        RagAnswerContextBuilder contextBuilder = new RagAnswerContextBuilder();

        int chunkCount = indexService.indexDocument(documentId, documentText).size();
        if (chunkCount == 0) {
            return RagQaContext.empty();
        }
        List<VectorSearchResult> hits = retrievalService.retrieveForQuestion(documentId, question, resolvedTopK);
        if (hits.isEmpty()) {
            return new RagQaContext(false, "", List.of(), chunkCount, 0);
        }

        RagAnswerContext answerContext = contextBuilder.build(hits);
        String contextText = truncate(answerContext.contextText(), resolvedMaxContextChars);
        if (contextText.isBlank()) {
            return new RagQaContext(false, "", answerContext.citations(), chunkCount, hits.size());
        }
        return new RagQaContext(true, contextText, answerContext.citations(), chunkCount, hits.size());
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength);
    }
}
