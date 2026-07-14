package com.docpilot.backend.ai.rag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RagEvidenceQuoteExtractor {

    private static final int QUOTE_MAX_LENGTH = 180;
    private static final Pattern EVIDENCE_MARKER = Pattern.compile("\\b[A-Za-z0-9]+(?:-[A-Za-z0-9]+){2,}\\b");

    private RagEvidenceQuoteExtractor() {
    }

    static EvidenceQuote extract(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return new EvidenceQuote("", 0, 0);
        }

        Matcher matcher = EVIDENCE_MARKER.matcher(text);
        if (matcher.find()) {
            return boundedQuote(text, matcher.start(), matcher.end());
        }
        return firstReadableQuote(text);
    }

    private static EvidenceQuote boundedQuote(String text, int anchorStart, int anchorEnd) {
        int start = previousBoundary(text, anchorStart);
        int end = nextBoundary(text, anchorEnd);
        if (end - start > QUOTE_MAX_LENGTH) {
            int markerLength = anchorEnd - anchorStart;
            int sideBudget = Math.max(0, (QUOTE_MAX_LENGTH - markerLength) / 2);
            start = Math.max(0, anchorStart - sideBudget);
            end = Math.min(text.length(), start + QUOTE_MAX_LENGTH);
            if (end < anchorEnd) {
                end = anchorEnd;
                start = Math.max(0, end - QUOTE_MAX_LENGTH);
            }
            start = trimForward(text, start, anchorStart);
            end = trimBackward(text, end, anchorEnd);
        }
        return new EvidenceQuote(text.substring(start, end).trim(), start, end);
    }

    private static EvidenceQuote firstReadableQuote(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = nextBoundary(text, start);
        if (end - start > QUOTE_MAX_LENGTH) {
            end = Math.min(text.length(), start + QUOTE_MAX_LENGTH);
            end = trimBackward(text, end, start);
        }
        return new EvidenceQuote(text.substring(start, end).trim(), start, end);
    }

    private static int previousBoundary(String text, int anchorStart) {
        for (int i = anchorStart - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '.' || c == '!' || c == '?' || c == ';') {
                return trimForward(text, i + 1, anchorStart);
            }
        }
        return 0;
    }

    private static int nextBoundary(String text, int anchorEnd) {
        for (int i = anchorEnd; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '.' || c == '!' || c == '?' || c == ';') {
                return trimBackward(text, i + 1, anchorEnd);
            }
        }
        return text.length();
    }

    private static int trimForward(String text, int start, int anchorStart) {
        int result = start;
        while (result < anchorStart && Character.isWhitespace(text.charAt(result))) {
            result++;
        }
        return result;
    }

    private static int trimBackward(String text, int end, int anchorEnd) {
        int result = end;
        while (result > anchorEnd && Character.isWhitespace(text.charAt(result - 1))) {
            result--;
        }
        return result;
    }

    record EvidenceQuote(String text, int startOffset, int endOffset) {
    }
}
