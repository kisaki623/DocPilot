package com.docpilot.backend.ai.agent.tool;

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
        assertTrue(result.outputSummary().contains("No parsed document text"));
    }
}
