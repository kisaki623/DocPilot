package com.docpilot.backend.ai.rag;

import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagQaContextBuilder {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final RagEmbeddingProperties embeddingProperties;
    private final InMemoryVectorStore vectorStore;
    private final RagIndexManager indexManager;

    public RagQaContextBuilder() {
        this(new EmbeddingModelFactory(), new RagEmbeddingProperties(), new InMemoryVectorStore(), new RagIndexManager());
    }

    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory,
                               RagEmbeddingProperties embeddingProperties) {
        this(embeddingModelFactory, embeddingProperties, new InMemoryVectorStore(), new RagIndexManager());
    }

    @Autowired
    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory,
                               RagEmbeddingProperties embeddingProperties,
                               InMemoryVectorStore vectorStore,
                               RagIndexManager indexManager) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.embeddingProperties = embeddingProperties;
        this.vectorStore = vectorStore;
        this.indexManager = indexManager;
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
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                vectorStore,
                indexManager,
                embeddingProvider,
                RagIndexManager.VECTOR_STORE_IN_MEMORY,
                RagIndexService.DEFAULT_CHUNK_SIZE,
                RagIndexService.DEFAULT_CHUNK_OVERLAP
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, vectorStore);
        RagAnswerContextBuilder contextBuilder = new RagAnswerContextBuilder();

        RagIndexService.RagIndexResult indexResult = indexService.indexDocument(documentId, RagIndexKey.DEFAULT_VERSION, documentText);
        int chunkCount = indexResult.chunkCount();
        boolean indexReused = indexResult.state().indexReused();
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
                    0,
                    indexReused
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
                    0,
                    indexReused
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
                answerContext.citations().size(),
                indexReused
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
