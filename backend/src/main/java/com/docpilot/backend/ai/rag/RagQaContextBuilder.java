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
        int resolvedTopK = Math.max(1, topK);
        int resolvedMaxContextChars = Math.max(1, maxContextChars);
        String embeddingProvider = embeddingProperties.getProvider();
        if (documentText == null || documentText.isBlank()) {
            return RagQaContext.empty(RagQaTrace.retrieval(
                    embeddingProvider,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0
            ));
        }

        EmbeddingModel embeddingModel = embeddingModelFactory.create(embeddingProperties);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        RagIndexService indexService = new RagIndexService(embeddingModel, vectorStore);
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        RagAnswerContextBuilder contextBuilder = new RagAnswerContextBuilder();

        int chunkCount = indexService.indexDocument(documentId, documentText).size();
        if (chunkCount == 0) {
            return RagQaContext.empty(RagQaTrace.retrieval(
                    embeddingProvider,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0
            ));
        }
        List<VectorSearchResult> hits = retrievalService.retrieveForQuestion(documentId, question, resolvedTopK);
        if (hits.isEmpty()) {
            return new RagQaContext(false, "", List.of(), chunkCount, 0, RagQaTrace.retrieval(
                    embeddingProvider,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0
            ));
        }

        RagAnswerContext answerContext = contextBuilder.build(hits);
        String rawContextText = answerContext.contextText();
        String contextText = truncate(rawContextText, resolvedMaxContextChars);
        boolean contextTruncated = rawContextText != null && rawContextText.length() > contextText.length();
        RagQaTrace trace = RagQaTrace.retrieval(
                embeddingProvider,
                true,
                resolvedTopK,
                hits.size(),
                resolvedMaxContextChars,
                contextText.length(),
                contextTruncated,
                !contextText.isBlank(),
                answerContext.citations().size()
        );
        if (contextText.isBlank()) {
            return new RagQaContext(false, "", answerContext.citations(), chunkCount, hits.size(), trace);
        }
        return new RagQaContext(true, contextText, answerContext.citations(), chunkCount, hits.size(), trace);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength);
    }
}
