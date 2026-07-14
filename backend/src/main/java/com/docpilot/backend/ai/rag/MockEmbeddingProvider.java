package com.docpilot.backend.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER = "mock";
    private static final int DEFAULT_DIMENSION = 32;
    private static final Pattern TERM_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    private final int dimension;
    private final String model;

    public MockEmbeddingProvider() {
        this(DEFAULT_DIMENSION, "");
    }

    public MockEmbeddingProvider(int dimension) {
        this(dimension, "");
    }

    public MockEmbeddingProvider(int dimension, String model) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        this.dimension = dimension;
        this.model = model == null ? "" : model.trim();
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        EmbeddingRequest resolvedRequest = request == null ? EmbeddingRequest.of("") : request;
        String resultModel = resolvedModel(resolvedRequest.model());
        return new EmbeddingResult(
                toVector(resolvedRequest.input()),
                PROVIDER,
                resultModel,
                dimension,
                resolvedRequest.metadata()
        );
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<EmbeddingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(this::embed)
                .toList();
    }

    private EmbeddingVector toVector(String text) {
        if (text == null || text.isBlank()) {
            return zeroVector();
        }

        double[] vector = new double[dimension];
        for (String term : extractTerms(text)) {
            int hash = term.hashCode();
            int index = Math.floorMod(hash, dimension);
            double sign = Math.floorMod(hash / Math.max(1, dimension), 2) == 0 ? 1.0D : -1.0D;
            vector[index] += sign;
        }
        return normalize(vector);
    }

    private String resolvedModel(String requestModel) {
        if (requestModel != null && !requestModel.isBlank()) {
            return requestModel.trim();
        }
        return model;
    }

    private List<String> extractTerms(String text) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = TERM_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            terms.add(token);
            if (containsHan(token)) {
                addCjkTerms(token, terms);
            }
        }
        return terms;
    }

    private boolean containsHan(String token) {
        return HAN_PATTERN.matcher(token).find();
    }

    private void addCjkTerms(String token, List<String> terms) {
        List<String> chars = token.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .toList();
        terms.addAll(chars);
        for (int i = 0; i < chars.size() - 1; i++) {
            terms.add(chars.get(i) + chars.get(i + 1));
        }
    }

    private EmbeddingVector zeroVector() {
        List<Double> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(0.0D);
        }
        return new EmbeddingVector(values);
    }

    private EmbeddingVector normalize(double[] vector) {
        double norm = 0.0D;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        List<Double> values = new ArrayList<>(dimension);
        if (norm == 0.0D) {
            for (int i = 0; i < dimension; i++) {
                values.add(0.0D);
            }
            return new EmbeddingVector(values);
        }
        for (double value : vector) {
            values.add(value / norm);
        }
        return new EmbeddingVector(values);
    }
}
