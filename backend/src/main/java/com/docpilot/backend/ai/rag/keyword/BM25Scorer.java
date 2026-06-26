package com.docpilot.backend.ai.rag.keyword;

import java.util.*;
import java.util.regex.Pattern;

/**
 * BM25 (Best Matching 25) scoring algorithm for keyword-based retrieval.
 * <p>
 * BM25 is a probabilistic ranking function that calculates relevance scores
 * based on term frequency and inverse document frequency, with length normalization.
 * </p>
 * <p>
 * Score formula: BM25(D, Q) = Σ IDF(qi) * (f(qi, D) * (k1 + 1)) / (f(qi, D) + k1 * (1 - b + b * |D| / avgdl))
 * </p>
 * <p>
 * Parameters:
 * - k1: controls term frequency saturation (default: 1.5)
 * - b: controls length normalization (default: 0.75)
 * </p>
 */
public class BM25Scorer {

    private static final double DEFAULT_K1 = 1.5;
    private static final double DEFAULT_B = 0.75;
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    private final double k1;
    private final double b;
    private final Map<String, Double> idfCache = new HashMap<>();
    private double avgDocLength = 0.0;

    public BM25Scorer() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    public BM25Scorer(double k1, double b) {
        if (k1 < 0) {
            throw new IllegalArgumentException("k1 must be non-negative");
        }
        if (b < 0 || b > 1) {
            throw new IllegalArgumentException("b must be between 0 and 1");
        }
        this.k1 = k1;
        this.b = b;
    }

    /**
     * Initialize the scorer with a corpus to calculate IDF values and average document length.
     *
     * @param documents list of documents in the corpus
     */
    public void initializeCorpus(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            this.avgDocLength = 0.0;
            this.idfCache.clear();
            return;
        }

        // Calculate average document length
        double totalLength = 0.0;
        for (String doc : documents) {
            totalLength += tokenize(doc).size();
        }
        this.avgDocLength = totalLength / documents.size();

        // Calculate IDF for all terms
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String doc : documents) {
            Set<String> uniqueTerms = new HashSet<>(tokenize(doc));
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        int totalDocs = documents.size();
        idfCache.clear();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            String term = entry.getKey();
            int docFreq = entry.getValue();
            // IDF formula: log((N - df + 0.5) / (df + 0.5) + 1)
            double idf = Math.log((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0);
            idfCache.put(term, idf);
        }
    }

    /**
     * Score a document against a query using BM25.
     *
     * @param query    the search query
     * @param document the document to score
     * @return BM25 score (higher is more relevant)
     */
    public double score(String query, String document) {
        if (query == null || query.isBlank() || document == null || document.isBlank()) {
            return 0.0;
        }

        List<String> queryTerms = tokenize(query);
        List<String> docTerms = tokenize(document);

        if (queryTerms.isEmpty() || docTerms.isEmpty()) {
            return 0.0;
        }

        // Calculate term frequencies in document
        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : docTerms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        double docLength = docTerms.size();
        double score = 0.0;

        for (String queryTerm : queryTerms) {
            if (!termFreq.containsKey(queryTerm)) {
                continue;
            }

            double idf = idfCache.getOrDefault(queryTerm, 0.0);
            int freq = termFreq.get(queryTerm);

            // BM25 formula component for this term
            double numerator = freq * (k1 + 1.0);
            double denominator = freq + k1 * (1.0 - b + b * (docLength / avgDocLength));
            score += idf * (numerator / denominator);
        }

        return score;
    }

    /**
     * Tokenize text into terms for BM25 scoring.
     * Supports both CJK (character-level) and Latin (word-level) tokenization.
     *
     * @param text the text to tokenize
     * @return list of tokens
     */
    List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        String normalized = text.toLowerCase().trim();

        // Check if text contains CJK characters
        boolean hasCjk = CJK_PATTERN.matcher(normalized).find();

        if (hasCjk) {
            // CJK: character-level tokenization
            for (char c : normalized.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    tokens.add(String.valueOf(c));
                }
            }
        } else {
            // Latin: word-level tokenization
            var matcher = WORD_PATTERN.matcher(normalized);
            while (matcher.find()) {
                String word = matcher.group();
                if (!word.isEmpty()) {
                    tokens.add(word);
                }
            }
        }

        return tokens;
    }

    public double getK1() {
        return k1;
    }

    public double getB() {
        return b;
    }

    public double getAvgDocLength() {
        return avgDocLength;
    }
}
