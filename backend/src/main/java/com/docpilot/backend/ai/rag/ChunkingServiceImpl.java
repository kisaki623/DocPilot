package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ChunkingServiceImpl implements ChunkingService {

    @Override
    public List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text) {
        return chunk(documentId, userId, text, ChunkingOptions.defaults());
    }

    @Override
    public List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text, ChunkingOptions options) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        ChunkingOptions resolvedOptions = options == null ? ChunkingOptions.defaults() : options;
        String normalizedText = normalize(text);
        if (normalizedText.isBlank()) {
            return List.of();
        }

        List<TextSpan> blocks = findTextBlocks(normalizedText);
        List<TextSpan> chunkSpans = packBlocks(normalizedText, blocks, resolvedOptions);
        List<DocumentChunkCandidate> chunks = new ArrayList<>();
        for (TextSpan chunkSpan : chunkSpans) {
            appendChunks(documentId, userId, normalizedText, chunkSpan, resolvedOptions, chunks);
        }
        return List.copyOf(chunks);
    }

    private void appendChunks(Long documentId,
                              Long userId,
                              String normalizedText,
                              TextSpan paragraph,
                              ChunkingOptions options,
                              List<DocumentChunkCandidate> chunks) {
        int start = paragraph.startOffset();
        int paragraphEnd = paragraph.endOffset();
        while (start < paragraphEnd) {
            int end = Math.min(start + options.chunkSize(), paragraphEnd);
            TextSpan chunkSpan = trimSpan(normalizedText, start, end);
            if (!chunkSpan.isEmpty()) {
                String content = normalizedText.substring(chunkSpan.startOffset(), chunkSpan.endOffset()).trim();
                chunks.add(new DocumentChunkCandidate(
                        documentId,
                        userId,
                        chunks.size(),
                        content,
                        sha256(content),
                        chunkSpan.startOffset(),
                        chunkSpan.endOffset(),
                        content.length()
                ));
            }
            if (end == paragraphEnd) {
                break;
            }
            start = Math.max(end - options.overlap(), start + 1);
        }
    }

    private List<TextSpan> findTextBlocks(String text) {
        List<TextSpan> blocks = new ArrayList<>();
        int blockStart = -1;
        int lineStart = 0;
        boolean inFence = false;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(lineStart, lineEnd);
            boolean fenceLine = isFenceLine(line);
            if (fenceLine) {
                if (blockStart < 0) {
                    blockStart = lineStart;
                }
                inFence = !inFence;
            }
            if (blockStart < 0 && !line.isBlank()) {
                blockStart = lineStart;
            }
            if (!inFence && line.isBlank()) {
                addTrimmedBlock(text, blockStart, lineStart, blocks);
                blockStart = -1;
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        addTrimmedBlock(text, blockStart, text.length(), blocks);
        return blocks;
    }

    private void addTrimmedBlock(String text, int start, int end, List<TextSpan> blocks) {
        if (start < 0) {
            return;
        }
        TextSpan span = trimSpan(text, start, end);
        if (!span.isEmpty()) {
            blocks.add(span);
        }
    }

    private boolean isFenceLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private List<TextSpan> packBlocks(String text, List<TextSpan> blocks, ChunkingOptions options) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<TextSpan> packed = new ArrayList<>();
        TextSpan current = null;
        for (TextSpan block : blocks) {
            if (current == null) {
                current = block;
                continue;
            }
            TextSpan merged = trimSpan(text, current.startOffset(), block.endOffset());
            if (merged.length() <= options.chunkSize()) {
                current = merged;
                continue;
            }
            packed.add(current);
            current = block;
        }
        if (current != null) {
            packed.add(current);
        }
        return List.copyOf(packed);
    }

    private TextSpan trimSpan(String text, int start, int end) {
        int resolvedStart = start;
        int resolvedEnd = end;
        while (resolvedStart < resolvedEnd && Character.isWhitespace(text.charAt(resolvedStart))) {
            resolvedStart++;
        }
        while (resolvedEnd > resolvedStart && Character.isWhitespace(text.charAt(resolvedEnd - 1))) {
            resolvedEnd--;
        }
        return new TextSpan(resolvedStart, resolvedEnd);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record TextSpan(int startOffset, int endOffset) {

        private boolean isEmpty() {
            return endOffset <= startOffset;
        }

        private int length() {
            return Math.max(0, endOffset - startOffset);
        }
    }
}
