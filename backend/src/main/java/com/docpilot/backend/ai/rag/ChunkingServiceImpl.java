package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkingServiceImpl implements ChunkingService {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n[ \\t]*\\n(?:[ \\t]*\\n)*");

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

        List<TextSpan> paragraphs = findParagraphs(normalizedText);
        List<DocumentChunkCandidate> chunks = new ArrayList<>();
        for (TextSpan paragraph : paragraphs) {
            appendChunks(documentId, userId, normalizedText, paragraph, resolvedOptions, chunks);
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

    private List<TextSpan> findParagraphs(String text) {
        List<TextSpan> paragraphs = new ArrayList<>();
        Matcher matcher = PARAGRAPH_SEPARATOR.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addTrimmedParagraph(text, start, matcher.start(), paragraphs);
            start = matcher.end();
        }
        addTrimmedParagraph(text, start, text.length(), paragraphs);
        return paragraphs;
    }

    private void addTrimmedParagraph(String text, int start, int end, List<TextSpan> paragraphs) {
        TextSpan span = trimSpan(text, start, end);
        if (!span.isEmpty()) {
            paragraphs.add(span);
        }
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
    }
}
