package com.docpilot.backend.memory.eval;

import java.util.List;
import java.util.Map;

public record MemoryQualityEvalCase(
        String id,
        String category,
        Long userId,
        Long conversationId,
        String currentMessage,
        String contextMode,
        Long boundKnowledgeBaseId,
        boolean summaryEnabled,
        boolean memoryEnabled,
        String summaryText,
        List<EvalMessage> messages,
        List<EvalMemory> memories,
        String sensitiveProbe,
        boolean expectSensitiveRejected,
        int ragEvidenceCount,
        boolean ragRequired,
        Map<Long, Integer> documentHitCounts,
        List<String> expectedSuggestionTypes,
        List<Long> expectedSelectedMemoryIds,
        List<String> forbiddenSuggestionMarkers,
        List<Long> forbiddenSelectedMemoryIds,
        Map<String, Integer> expectedTraceCounts
) {

    public MemoryQualityEvalCase {
        id = id == null ? "" : id.trim();
        category = category == null ? "" : category.trim();
        currentMessage = currentMessage == null ? "" : currentMessage.trim();
        contextMode = contextMode == null ? "" : contextMode.trim();
        summaryText = summaryText == null ? "" : summaryText.trim();
        messages = messages == null ? List.of() : List.copyOf(messages);
        memories = memories == null ? List.of() : List.copyOf(memories);
        sensitiveProbe = sensitiveProbe == null ? "" : sensitiveProbe.trim();
        documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
        expectedSuggestionTypes = expectedSuggestionTypes == null ? List.of() : List.copyOf(expectedSuggestionTypes);
        expectedSelectedMemoryIds = expectedSelectedMemoryIds == null ? List.of() : List.copyOf(expectedSelectedMemoryIds);
        forbiddenSuggestionMarkers = forbiddenSuggestionMarkers == null ? List.of() : List.copyOf(forbiddenSuggestionMarkers);
        forbiddenSelectedMemoryIds = forbiddenSelectedMemoryIds == null ? List.of() : List.copyOf(forbiddenSelectedMemoryIds);
        expectedTraceCounts = expectedTraceCounts == null ? Map.of() : Map.copyOf(expectedTraceCounts);
    }

    public record EvalMessage(
            Long id,
            String role,
            String content,
            Integer sequenceNo
    ) {
        public EvalMessage {
            role = role == null ? "" : role.trim();
            content = content == null ? "" : content.trim();
        }
    }

    public record EvalMemory(
            Long id,
            String status,
            String memoryType,
            String content,
            Integer priority
    ) {
        public EvalMemory {
            status = status == null ? "" : status.trim();
            memoryType = memoryType == null ? "" : memoryType.trim();
            content = content == null ? "" : content.trim();
        }
    }
}
