package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.EmbeddingModel;
import com.docpilot.backend.ai.rag.EmbeddingModelFactory;
import com.docpilot.backend.ai.rag.InMemoryVectorStore;
import com.docpilot.backend.ai.rag.RagAnswerContext;
import com.docpilot.backend.ai.rag.RagAnswerContextBuilder;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexKey;
import com.docpilot.backend.ai.rag.RagIndexManager;
import com.docpilot.backend.ai.rag.RagIndexService;
import com.docpilot.backend.ai.rag.RagRetrievalService;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.VectorStore;
import com.docpilot.backend.ai.rag.VectorStoreFactory;
import com.docpilot.backend.ai.rag.VectorSearchResult;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DocumentRagTool implements AgentTool<DocumentRagTool.RagInput, DocumentRagTool.RagResult> {

    public static final String TOOL_NAME = "document_rag_tool";
    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_TOP_K = 5;
    private static final int SNIPPET_MAX_LENGTH = 280;
    private static final int CONTEXT_MAX_LENGTH = 900;

    private final EmbeddingModelFactory embeddingModelFactory;
    private final RagEmbeddingProperties embeddingProperties;
    private final InMemoryVectorStore vectorStore;
    private final RagIndexManager indexManager;
    private final RagVectorStoreProperties vectorStoreProperties;
    private final VectorStoreFactory vectorStoreFactory;

    public DocumentRagTool() {
        this(new EmbeddingModelFactory(), new RagEmbeddingProperties(), new InMemoryVectorStore(), new RagIndexManager(),
                new RagVectorStoreProperties(), new VectorStoreFactory());
    }

    public DocumentRagTool(EmbeddingModelFactory embeddingModelFactory,
                           RagEmbeddingProperties embeddingProperties,
                           InMemoryVectorStore vectorStore,
                           RagIndexManager indexManager) {
        this(embeddingModelFactory, embeddingProperties, vectorStore, indexManager,
                new RagVectorStoreProperties(), new VectorStoreFactory());
    }

    @Autowired
    public DocumentRagTool(EmbeddingModelFactory embeddingModelFactory,
                           RagEmbeddingProperties embeddingProperties,
                           InMemoryVectorStore vectorStore,
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

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public RagResult execute(RagInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(input.documentId(), "documentId");
        ValidationUtils.requireNonBlank(input.task(), "task");

        String documentText = normalize(input.documentText());
        int topK = resolveTopK(input.topK());
        if (documentText.isEmpty()) {
            return new RagResult(
                    input.documentId(),
                    0,
                    topK,
                    List.of(),
                    List.of(),
                    "",
                    buildTraceSummary(topK, 0, false, true, "no_document_text", 0, false)
            );
        }

        EmbeddingModel embeddingModel = embeddingModelFactory.create(embeddingProperties);
        VectorStore selectedVectorStore = vectorStoreFactory.create(vectorStoreProperties, vectorStore);
        RagIndexService indexService = new RagIndexService(
                embeddingModel,
                selectedVectorStore,
                indexManager,
                embeddingProperties.getProvider(),
                vectorStoreProperties.getProvider(),
                RagIndexService.DEFAULT_CHUNK_SIZE,
                RagIndexService.DEFAULT_CHUNK_OVERLAP
        );
        RagRetrievalService retrievalService = new RagRetrievalService(embeddingModel, selectedVectorStore);
        RagAnswerContextBuilder contextBuilder = new RagAnswerContextBuilder();

        RagIndexService.RagIndexResult indexResult = indexService.indexDocument(
                input.documentId(),
                RagIndexKey.DEFAULT_VERSION,
                documentText
        );
        int chunkCount = indexResult.chunkCount();
        List<VectorSearchResult> hits = retrievalService.retrieveForQuestion(input.documentId(), input.task(), topK);
        RagAnswerContext answerContext = contextBuilder.build(hits);
        List<RetrievedChunk> retrievedChunks = toRetrievedChunks(hits);

        return new RagResult(
                input.documentId(),
                chunkCount,
                topK,
                retrievedChunks,
                answerContext.citations(),
                truncate(answerContext.contextText(), CONTEXT_MAX_LENGTH),
                buildTraceSummary(
                        topK,
                        retrievedChunks.size(),
                        !answerContext.contextText().isBlank(),
                        false,
                        "",
                        answerContext.citations().size(),
                        indexResult.state().indexReused()
                )
        );
    }

    private String buildTraceSummary(int topK,
                                     int retrievedCount,
                                     boolean contextHashPresent,
                                     boolean fallbackUsed,
                                     String fallbackReason,
                                     int citationCount,
                                     boolean indexReused) {
        return "embeddingProvider=" + embeddingProperties.getProvider()
                + ", vectorStoreType=" + vectorStoreProperties.getProvider()
                + ", topK=" + topK
                + ", retrievedCount=" + retrievedCount
                + ", contextHashPresent=" + contextHashPresent
                + ", fallbackUsed=" + fallbackUsed
                + ", fallbackReason=" + safeSummaryValue(fallbackReason)
                + ", citationCount=" + Math.max(0, citationCount)
                + ", indexReused=" + indexReused;
    }

    private List<RetrievedChunk> toRetrievedChunks(List<VectorSearchResult> hits) {
        java.util.ArrayList<RetrievedChunk> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchResult hit = hits.get(i);
            chunks.add(new RetrievedChunk(
                    i + 1,
                    hit.chunk().chunkIndex(),
                    hit.score(),
                    truncate(hit.chunk().text().trim(), SNIPPET_MAX_LENGTH),
                    hit.chunk().metadata()
            ));
        }
        return List.copyOf(chunks);
    }

    private int resolveTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String safeSummaryValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    public record RagInput(Long documentId, String task, String documentText, Integer topK) {
    }

    public record RagResult(Long documentId,
                            int chunkCount,
                            int topK,
                            List<RetrievedChunk> retrievedChunks,
                            List<com.docpilot.backend.ai.rag.RagCitation> citations,
                            String answerContext,
                            String outputSummary) {
    }

    public record RetrievedChunk(int rank,
                                 int chunkIndex,
                                 double score,
                                 String snippet,
                                 Map<String, String> metadata) {
    }
}
