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
                SectionContext section = sectionContext(normalizedText, chunkSpan.startOffset());
                chunks.add(new DocumentChunkCandidate(
                        documentId,
                        userId,
                        chunks.size(),
                        content,
                        sha256(content),
                        chunkSpan.startOffset(),
                        chunkSpan.endOffset(),
                        content.length(),
                        section.title(),
                        section.ordinal(),
                        paragraph.startBlockOrdinal(),
                        structureType(content),
                        qualityFlags(content, options)
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
        int blockOrdinal = 0;
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
                blockOrdinal = addTrimmedBlock(text, blockStart, lineStart, blockOrdinal, blocks);
                blockStart = -1;
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        addTrimmedBlock(text, blockStart, text.length(), blockOrdinal, blocks);
        return blocks;
    }

    private int addTrimmedBlock(String text, int start, int end, int blockOrdinal, List<TextSpan> blocks) {
        if (start < 0) {
            return blockOrdinal;
        }
        TextSpan span = trimSpan(text, start, end, blockOrdinal, blockOrdinal);
        if (!span.isEmpty()) {
            blocks.add(span);
            return blockOrdinal + 1;
        }
        return blockOrdinal;
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
            TextSpan merged = trimSpan(text, current.startOffset(), block.endOffset(),
                    current.startBlockOrdinal(), block.endBlockOrdinal());
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
        return trimSpan(text, start, end, 0, 0);
    }

    private TextSpan trimSpan(String text, int start, int end, int startBlockOrdinal, int endBlockOrdinal) {
        int resolvedStart = start;
        int resolvedEnd = end;
        while (resolvedStart < resolvedEnd && Character.isWhitespace(text.charAt(resolvedStart))) {
            resolvedStart++;
        }
        while (resolvedEnd > resolvedStart && Character.isWhitespace(text.charAt(resolvedEnd - 1))) {
            resolvedEnd--;
        }
        return new TextSpan(resolvedStart, resolvedEnd, startBlockOrdinal, endBlockOrdinal);
    }

    private SectionContext sectionContext(String text, int offset) {
        int ordinal = 0;
        String title = "";
        int lineStart = 0;
        while (lineStart <= text.length() && lineStart <= offset) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String heading = markdownHeadingTitle(text.substring(lineStart, lineEnd));
            if (!heading.isBlank()) {
                ordinal++;
                title = heading;
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return new SectionContext(title, ordinal);
    }

    private String markdownHeadingTitle(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        int depth = 0;
        while (depth < trimmed.length() && trimmed.charAt(depth) == '#') {
            depth++;
        }
        if (depth == 0 || depth > 6 || depth >= trimmed.length() || !Character.isWhitespace(trimmed.charAt(depth))) {
            return "";
        }
        return trimmed.substring(depth).trim();
    }

    private String structureType(String content) {
        String firstLine = firstNonBlankLine(content);
        if (!markdownHeadingTitle(firstLine).isBlank()) {
            return "section";
        }
        if (content.lines().anyMatch(this::isFenceLine)) {
            return "code";
        }
        return "paragraph";
    }

    private String qualityFlags(String content, ChunkingOptions options) {
        List<String> flags = new ArrayList<>();
        if (content.length() < Math.min(80, Math.max(1, options.chunkSize() / 4))) {
            flags.add("short");
        }
        if (content.indexOf('\uFFFD') >= 0) {
            flags.add("replacement_char");
        }
        return flags.isEmpty() ? "none" : String.join(",", flags);
    }

    private String firstNonBlankLine(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
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

    private record TextSpan(int startOffset, int endOffset, int startBlockOrdinal, int endBlockOrdinal) {

        private boolean isEmpty() {
            return endOffset <= startOffset;
        }

        private int length() {
            return Math.max(0, endOffset - startOffset);
        }
    }

    private record SectionContext(String title, int ordinal) {
    }
}
