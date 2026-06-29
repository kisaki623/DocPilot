package com.docpilot.backend.ai.rag.rewrite;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RuleBasedQueryRewriteService implements QueryRewriteService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ENGLISH_QUESTION_PREFIX = Pattern.compile(
            "^(please\\s+)?(tell\\s+me\\s+|explain\\s+|summarize\\s+|list\\s+|show\\s+|what\\s+is\\s+|what\\s+are\\s+|which\\s+|where\\s+|when\\s+|who\\s+|how\\s+)+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHINESE_QUESTION_WORDS = Pattern.compile("(请|帮我|说明|解释|总结|概括|列出|引用来源|必须覆盖|分别|哪些|什么|如何|为什么|请问)");

    @Override
    public List<QueryRewriteVariant> rewrite(String query, int maxVariants) {
        int limit = Math.max(1, Math.min(5, maxVariants));
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate(normalized, "original"));
        candidates.add(new Candidate(cleanQuestionWords(normalized), "cleaned_question"));
        candidates.addAll(splitComparisonQuery(normalized));

        Set<String> seen = new LinkedHashSet<>();
        List<QueryRewriteVariant> variants = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String candidateQuery = normalize(candidate.query());
            if (candidateQuery.isBlank() || !seen.add(candidateQuery.toLowerCase(Locale.ROOT))) {
                continue;
            }
            variants.add(new QueryRewriteVariant(candidateQuery, candidate.strategy(), variants.size()));
            if (variants.size() >= limit) {
                break;
            }
        }
        return List.copyOf(variants);
    }

    private String cleanQuestionWords(String query) {
        String cleaned = ENGLISH_QUESTION_PREFIX.matcher(query).replaceFirst("");
        cleaned = CHINESE_QUESTION_WORDS.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replace("?", " ").replace("？", " ");
        return normalize(cleaned);
    }

    private List<Candidate> splitComparisonQuery(String query) {
        String cleaned = cleanQuestionWords(query);
        if (cleaned.isBlank()) {
            return List.of();
        }
        String[] parts;
        if (cleaned.contains(" and ")) {
            parts = cleaned.split("(?i)\\s+and\\s+");
        } else if (cleaned.contains(" 和 ")) {
            parts = cleaned.split("\\s+和\\s+");
        } else if (cleaned.contains(" vs ")) {
            parts = cleaned.split("(?i)\\s+vs\\s+");
        } else {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalize(part);
            if (!normalized.isBlank()) {
                candidates.add(new Candidate(normalized, "comparison_part"));
            }
        }
        return candidates;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    private record Candidate(String query, String strategy) {
    }
}
