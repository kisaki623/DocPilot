package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.rag.EmbeddingModelFactory;
import com.docpilot.backend.ai.rag.InMemoryVectorStore;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexManager;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.VectorStoreFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRagToolTest {

    private final DocumentRagTool tool = new DocumentRagTool();

    @Test
    void shouldReturnTopKRetrievedChunks() {
        DocumentRagTool.RagResult result = tool.execute(new DocumentRagTool.RagInput(
                61L,
                "retrieve payment terms",
                """
                        Payment terms require settlement within 30 days after invoice.
                        Delivery terms mention acceptance testing and handover documents.
                        Warranty terms include a twelve month support period.
                        """,
                2
        ));

        assertEquals(61L, result.documentId());
        assertTrue(result.chunkCount() >= 1);
        assertEquals(2, result.topK());
        assertFalse(result.retrievedChunks().isEmpty());
        assertTrue(result.retrievedChunks().size() <= 2);
        assertFalse(result.answerContext().isBlank());
        assertFalse(result.citations().isEmpty());
        assertTrue(result.retrievedChunks().get(0).metadata().containsKey("contentHash"));
        assertTrue(result.outputSummary().contains("embeddingProvider=fake"));
        assertTrue(result.outputSummary().contains("vectorStoreType=in_memory"));
        assertTrue(result.outputSummary().contains("topK=2"));
        assertTrue(result.outputSummary().contains("retrievedCount="));
        assertTrue(result.outputSummary().contains("contextHashPresent=true"));
        assertTrue(result.outputSummary().contains("fallbackUsed=false"));
        assertTrue(result.outputSummary().contains("citationCount="));
        assertFalse(result.outputSummary().contains("Payment terms require"));
    }

    @Test
    void shouldKeepDocumentIdIsolated() {
        DocumentRagTool.RagResult first = tool.execute(new DocumentRagTool.RagInput(
                100L,
                "retrieve invoice",
                "Invoice payment and finance clauses.",
                1
        ));
        DocumentRagTool.RagResult second = tool.execute(new DocumentRagTool.RagInput(
                200L,
                "retrieve logistics",
                "Logistics delivery and warehouse clauses.",
                1
        ));

        assertEquals(100L, first.retrievedChunks().isEmpty() ? first.documentId() : first.documentId());
        assertEquals(200L, second.retrievedChunks().isEmpty() ? second.documentId() : second.documentId());
        assertTrue(first.retrievedChunks().stream().allMatch(chunk -> chunk.metadata().containsKey("chunkVersion")));
        assertTrue(second.retrievedChunks().stream().allMatch(chunk -> chunk.metadata().containsKey("chunkVersion")));
        assertTrue(first.retrievedChunks().stream().allMatch(chunk -> "100".equals(chunk.metadata().get("documentId"))));
        assertTrue(second.retrievedChunks().stream().allMatch(chunk -> "200".equals(chunk.metadata().get("documentId"))));
    }

    @Test
    void shouldReturnFriendlyEmptyResultWhenTextMissing() {
        DocumentRagTool.RagResult result = tool.execute(new DocumentRagTool.RagInput(
                61L,
                "retrieve terms",
                "   ",
                3
        ));

        assertEquals(0, result.chunkCount());
        assertTrue(result.retrievedChunks().isEmpty());
        assertTrue(result.citations().isEmpty());
        assertTrue(result.outputSummary().contains("fallbackUsed=true"));
        assertTrue(result.outputSummary().contains("fallbackReason=no_document_text"));
    }

    @Test
    void shouldReturnFriendlyEmptyResultWhenVectorStoreFails() {
        RagVectorStoreProperties vectorStoreProperties = new RagVectorStoreProperties();
        vectorStoreProperties.setProvider("qdrant_disabled");
        DocumentRagTool failingTool = new DocumentRagTool(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                new InMemoryVectorStore(),
                new RagIndexManager(),
                vectorStoreProperties,
                new VectorStoreFactory()
        );

        DocumentRagTool.RagResult result = failingTool.execute(new DocumentRagTool.RagInput(
                61L,
                "retrieve terms",
                "Payment terms require settlement within 30 days after invoice.",
                2
        ));

        assertEquals(61L, result.documentId());
        assertEquals(0, result.chunkCount());
        assertTrue(result.retrievedChunks().isEmpty());
        assertTrue(result.citations().isEmpty());
        assertTrue(result.answerContext().isBlank());
        assertTrue(result.outputSummary().contains("vectorStoreType=qdrant_disabled"));
        assertTrue(result.outputSummary().contains("fallbackUsed=true"));
        assertTrue(result.outputSummary().contains("fallbackReason=qdrant_disabled"));
        assertFalse(result.outputSummary().contains("Payment terms require"));
    }
}
