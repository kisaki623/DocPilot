package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return markDuplicateChunks(chunks);
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
                SectionContext section = sectionContext(normalizedText, chunkSpan.startOffset(), chunkSpan.endOffset());
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
                        section.path(),
                        paragraph.startBlockOrdinal(),
                        structureType(content),
                        qualityFlags(
                                content,
                                options,
                                chunkSpan.startOffset() > paragraph.startOffset(),
                                chunkSpan.endOffset() < paragraph.endOffset()
                        )
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

    private SectionContext sectionContext(String text, int startOffset, int endOffset) {
        int ordinal = 0;
        int primaryOrdinal = 0;
        String title = "";
        List<String> path = new ArrayList<>();
        int lineStart = 0;
        int resolvedEnd = Math.max(startOffset, endOffset);
        while (lineStart <= text.length() && lineStart <= resolvedEnd) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            Heading heading = markdownHeading(text.substring(lineStart, lineEnd));
            if (!heading.title().isBlank()) {
                ordinal++;
                if (lineStart <= startOffset) {
                    title = heading.title();
                    primaryOrdinal = ordinal;
                }
                while (path.size() >= heading.depth()) {
                    path.remove(path.size() - 1);
                }
                path.add(heading.title());
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return new SectionContext(title, primaryOrdinal, String.join(" / ", path));
    }

    private String markdownHeadingTitle(String line) {
        return markdownHeading(line).title();
    }

    private Heading markdownHeading(String line) {
        if (line == null) {
            return new Heading("", 0);
        }
        String trimmed = line.trim();
        int depth = 0;
        while (depth < trimmed.length() && trimmed.charAt(depth) == '#') {
            depth++;
        }
        if (depth == 0 || depth > 6 || depth >= trimmed.length() || !Character.isWhitespace(trimmed.charAt(depth))) {
            return new Heading("", 0);
        }
        return new Heading(trimmed.substring(depth).trim(), depth);
    }

    private String structureType(String content) {
        String firstLine = firstNonBlankLine(content);
        if (!markdownHeadingTitle(firstLine).isBlank()) {
            return "section";
        }
        if (content.lines().anyMatch(this::isFenceLine)) {
            return "code";
        }
        if (isTableBlock(content)) {
            return "table";
        }
        if (isListBlock(content)) {
            return "list";
        }
        return "paragraph";
    }

    private String qualityFlags(String content,
                                ChunkingOptions options,
                                boolean splitFromPreviousWindow,
                                boolean splitToNextWindow) {
        List<String> flags = new ArrayList<>();
        if (content.length() < Math.min(80, Math.max(1, options.chunkSize() / 4))) {
            flags.add("short");
        }
        if (content.indexOf('\uFFFD') >= 0) {
            flags.add("replacement_char");
        }
        if (splitFromPreviousWindow || splitToNextWindow) {
            flags.add("window_split");
        }
        if (splitToNextWindow && !endsAtNaturalBoundary(content)) {
            flags.add("mid_sentence_split");
        }
        if (isTableBlock(content) && !hasMarkdownTableSeparator(content)) {
            flags.add("table_header_weak");
        }
        return flags.isEmpty() ? "none" : String.join(",", flags);
    }

    private List<DocumentChunkCandidate> markDuplicateChunks(List<DocumentChunkCandidate> chunks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DocumentChunkCandidate chunk : chunks) {
            counts.merge(chunk.contentHash(), 1, Integer::sum);
        }
        List<DocumentChunkCandidate> marked = new ArrayList<>(chunks.size());
        for (DocumentChunkCandidate chunk : chunks) {
            if (counts.getOrDefault(chunk.contentHash(), 0) <= 1) {
                marked.add(chunk);
                continue;
            }
            marked.add(chunk.withQualityFlags(appendFlag(chunk.qualityFlags(), "duplicate_content")));
        }
        return List.copyOf(marked);
    }

    private String appendFlag(String qualityFlags, String flag) {
        if (qualityFlags == null || qualityFlags.isBlank() || "none".equals(qualityFlags)) {
            return flag;
        }
        if (List.of(qualityFlags.split(",")).contains(flag)) {
            return qualityFlags;
        }
        return qualityFlags + "," + flag;
    }

    private boolean isTableBlock(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        long tableLines = content.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("|") && line.endsWith("|"))
                .count();
        return tableLines >= 2;
    }

    private boolean hasMarkdownTableSeparator(String content) {
        return content != null && content.lines()
                .map(String::trim)
                .anyMatch(line -> line.matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"));
    }

    private boolean isListBlock(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        long listLines = content.lines()
                .map(String::trim)
                .filter(line -> line.matches("^([-*+]\\s+|\\d+\\.\\s+).+"))
                .count();
        return listLines >= 2;
    }

    private boolean endsAtNaturalBoundary(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String trimmed = content.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        return ".!?;:。！？；：)]}`\"'".indexOf(last) >= 0;
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

    private record Heading(String title, int depth) {
    }

    private record SectionContext(String title, int ordinal, String path) {
    }
}
