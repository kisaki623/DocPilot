package com.docpilot.backend.memory.service;

import java.util.List;

public interface MemoryExtractionService {

    List<MemorySuggestionCandidate> extractSuggestions(Long userId, Long conversationId, Integer limit);
}
