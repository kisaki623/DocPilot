package com.docpilot.backend.document.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ParserTextUtils {

    private ParserTextUtils() {
    }

    static ParseResult result(ParserInput input,
                              String fullText,
                              List<DocumentBlock> blocks,
                              Map<String, String> metadata,
                              List<String> warnings,
                              DocumentParser parser,
                              long startNanos,
                              int pageCount) {
        String normalized = normalize(fullText);
        if (normalized.isBlank()) {
            throw new ParserException(ParseErrorCode.EMPTY_CONTENT, "parser extracted no text");
        }
        return new ParseResult(
                input.documentId(),
                input.fileName(),
                input.contentType(),
                input.fileSize(),
                normalized,
                blocks,
                metadata,
                warnings,
                parser.parserName(),
                parser.parserVersion(),
                elapsedMillis(startNanos),
                normalized.length(),
                pageCount,
                blocks == null ? 0 : blocks.size()
        );
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static List<DocumentBlock> paragraphBlocks(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<DocumentBlock> blocks = new ArrayList<>();
        int blockStart = -1;
        int lineStart = 0;
        String currentSection = "";
        List<String> sectionPath = new ArrayList<>();
        while (lineStart <= normalized.length()) {
            int lineEnd = normalized.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = normalized.length();
            }
            String line = normalized.substring(lineStart, lineEnd);
            Heading heading = markdownHeading(line);
            if (!heading.title().isBlank()) {
                while (sectionPath.size() >= heading.depth()) {
                    sectionPath.remove(sectionPath.size() - 1);
                }
                sectionPath.add(heading.title());
                currentSection = heading.title();
            }
            if (blockStart < 0 && !line.isBlank()) {
                blockStart = lineStart;
            }
            if (line.isBlank()) {
                addBlock(normalized, blockStart, lineStart, currentSection, sectionPath, blocks);
                blockStart = -1;
            }
            if (lineEnd == normalized.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        addBlock(normalized, blockStart, normalized.length(), currentSection, sectionPath, blocks);
        return List.copyOf(blocks);
    }

    static int appendText(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return builder.length();
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        int start = builder.length();
        builder.append(text.trim());
        return start;
    }

    static long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static void addBlock(String text,
                                 int start,
                                 int end,
                                 String currentSection,
                                 List<String> sectionPath,
                                 List<DocumentBlock> blocks) {
        if (start < 0 || end <= start) {
            return;
        }
        int resolvedStart = start;
        int resolvedEnd = end;
        while (resolvedStart < resolvedEnd && Character.isWhitespace(text.charAt(resolvedStart))) {
            resolvedStart++;
        }
        while (resolvedEnd > resolvedStart && Character.isWhitespace(text.charAt(resolvedEnd - 1))) {
            resolvedEnd--;
        }
        if (resolvedEnd <= resolvedStart) {
            return;
        }
        String content = text.substring(resolvedStart, resolvedEnd).trim();
        BlockType type = markdownHeading(content).title().isBlank() ? BlockType.PARAGRAPH : BlockType.HEADING;
        blocks.add(new DocumentBlock(
                blocks.size(),
                type,
                content,
                null,
                currentSection,
                String.join(" / ", sectionPath),
                resolvedStart,
                resolvedEnd,
                "block:" + blocks.size()
        ));
    }

    private static Heading markdownHeading(String line) {
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

    private record Heading(String title, int depth) {
    }
}
