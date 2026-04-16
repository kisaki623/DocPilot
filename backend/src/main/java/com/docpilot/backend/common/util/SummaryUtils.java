package com.docpilot.backend.common.util;

import java.util.ArrayList;
import java.util.List;

public final class SummaryUtils {

    private static final int DEFAULT_SUMMARY_MAX_LENGTH = 200;

    private SummaryUtils() {
    }

    public static String buildSummary(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }

        int resolvedMaxLength = maxLength > 0 ? maxLength : DEFAULT_SUMMARY_MAX_LENGTH;
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() <= resolvedMaxLength) {
            return normalized;
        }

        String sentenceSummary = buildSentenceSummary(normalized, resolvedMaxLength);
        if (!sentenceSummary.isBlank()) {
            return sentenceSummary;
        }
        return normalized.substring(0, resolvedMaxLength);
    }

    private static String normalizeContent(String content) {
        String normalized = content
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>+\\s*", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return "";
        }

        return normalized;
    }

    private static String buildSentenceSummary(String normalized, int maxLength) {
        List<String> sentences = splitSentences(normalized);
        if (sentences.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder(maxLength);
        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (trimmedSentence.isEmpty()) {
                continue;
            }
            if (summary.isEmpty()) {
                if (trimmedSentence.length() > maxLength) {
                    return trimmedSentence.substring(0, maxLength);
                }
                summary.append(trimmedSentence);
                continue;
            }
            if (summary.length() + 1 + trimmedSentence.length() > maxLength) {
                break;
            }
            summary.append(' ').append(trimmedSentence);
        }

        return summary.toString().trim();
    }

    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);
            if (isSentenceDelimiter(ch)) {
                sentences.add(current.toString());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            sentences.add(current.toString());
        }

        return sentences;
    }

    private static boolean isSentenceDelimiter(char ch) {
        return ch == '。'
                || ch == '！'
                || ch == '？'
                || ch == ';'
                || ch == '；'
                || ch == '!'
                || ch == '?';
    }
}

