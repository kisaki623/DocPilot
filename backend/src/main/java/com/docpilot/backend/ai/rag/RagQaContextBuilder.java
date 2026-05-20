package com.docpilot.backend.ai.rag;

import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagQaContextBuilder {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final RagEmbeddingProperties embeddingProperties;
    private final VectorStore vectorStore;
    private final RagIndexManager indexManager;
    private final RagVectorStoreProperties vectorStoreProperties;
    private final VectorStoreFactory vectorStoreFactory;

    public RagQaContextBuilder() {
        this(new EmbeddingModelFactory(), new RagEmbeddingProperties());
    }

    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory,
                               RagEmbeddingProperties embeddingProperties) {
        this(embeddingModelFactory, embeddingProperties, new InMemoryVectorStore(), new RagIndexManager(),
                new RagVectorStoreProperties(), new VectorStoreFactory());
    }

    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory,
                               RagEmbeddingProperties embeddingProperties,
                               InMemoryVectorStore vectorStore,
                               RagIndexManager indexManager) {
        this(embeddingModelFactory, embeddingProperties, vectorStore, indexManager,
                new RagVectorStoreProperties(), new VectorStoreFactory());
    }

    @Autowired
    public RagQaContextBuilder(EmbeddingModelFactory embeddingModelFactory,
                               RagEmbeddingProperties embeddingProperties,
                               VectorStore vectorStore,
                               RagIndexManager indexManager,
                               RagVectorStoreProperties vectorStoreProperties,
                               VectorStoreFactory vectorStoreFactory) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.embeddingProperties = embeddingProperties;
        this.vectorStore = vectorStore;
        this.indexManager = indexManager;
        this.vectorStoreProperties = vectorStoreProperties == null ? new RagVectorStoreProperties() : vectorStoreProperties;
        this.vectorStoreFactory = vectorStoreFactory == null ? new VectorStoreFactory() : vectorStoreFactory;
    }

    public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonBlank(question, "question");
        int resolvedTopK = Math.max(1, topK);
        int resolvedMaxContextChars = Math.max(1, maxContextChars);
        String embeddingProvider = embeddingProperties.getProvider();
        String vectorStoreType = vectorStoreProperties.getProvider();
        if (documentText == null || documentText.isBlank()) {
            return RagQaContext.empty(RagQaTrace.retrieval(
                    embeddingProvider,
                    vectorStoreType,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0,
                    false
            ));
        }

        EmbeddingModel embeddingModel = embeddingModelFactory.create(embeddingProperties);
        VectorStore selectedVectorStore = vectorStoreFactory.create(vectorStoreProperties, vectorStore);
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                selectedVectorStore,
                indexManager,
                embeddingProvider,
                vectorStoreProperties.getProvider(),
                RagIndexService.DEFAULT_CHUNK_SIZE,
                RagIndexService.DEFAULT_CHUNK_OVERLAP
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, selectedVectorStore);
        RagAnswerContextBuilder contextBuilder = new RagAnswerContextBuilder();

        RagIndexService.RagIndexResult indexResult = indexService.indexDocument(documentId, RagIndexKey.DEFAULT_VERSION, documentText);
        int chunkCount = indexResult.chunkCount();
        boolean indexReused = indexResult.state().indexReused();
        boolean indexTruncated = indexResult.indexTruncated();
        if (chunkCount == 0) {
            return RagQaContext.empty(RagQaTrace.retrieval(
                    embeddingProvider,
                    vectorStoreType,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0,
                    indexReused,
                    indexTruncated
            ));
        }
        List<VectorSearchResult> hits = retrievalService.retrieveForQuestion(documentId, question, resolvedTopK);
        if (hits.isEmpty()) {
            return new RagQaContext(false, "", List.of(), chunkCount, 0, RagQaTrace.retrieval(
                    embeddingProvider,
                    vectorStoreType,
                    true,
                    resolvedTopK,
                    0,
                    resolvedMaxContextChars,
                    0,
                    false,
                    false,
                    0,
                    indexReused,
                    indexTruncated
            ));
        }

        RagAnswerContext answerContext = contextBuilder.build(hits);
        String rawContextText = answerContext.contextText();
        String contextText = truncate(rawContextText, resolvedMaxContextChars);
        boolean contextTruncated = rawContextText != null && rawContextText.length() > contextText.length();
        RagQaTrace trace = RagQaTrace.retrieval(
                embeddingProvider,
                vectorStoreType,
                true,
                resolvedTopK,
                hits.size(),
                resolvedMaxContextChars,
                contextText.length(),
                contextTruncated,
                !contextText.isBlank(),
                answerContext.citations().size(),
                indexReused,
                indexTruncated
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
