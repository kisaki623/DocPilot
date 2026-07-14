package com.docpilot.backend.document.parser;

import java.util.List;
import java.util.Map;

public record ParseResult(
        Long documentId,
        String fileName,
        String contentType,
        Long fileSize,
        String fullText,
        List<DocumentBlock> blocks,
        Map<String, String> metadata,
        List<String> warnings,
        String parserName,
        String parserVersion,
        long parseDurationMs,
        int extractedChars,
        int pageCount,
        int blockCount
) {

    public ParseResult {
        fullText = fullText == null ? "" : fullText.trim();
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        parserName = parserName == null ? "" : parserName.trim();
        parserVersion = parserVersion == null ? "" : parserVersion.trim();
        extractedChars = Math.max(extractedChars, fullText.length());
        blockCount = blockCount <= 0 ? blocks.size() : blockCount;
    }
}
