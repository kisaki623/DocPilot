package com.docpilot.backend.ai.rag.rewrite;

public record QueryRewriteVariant(
        String query,
        String strategy,
        int ordinal
) {

    public QueryRewriteVariant {
        query = query == null ? "" : query.trim();
        strategy = strategy == null ? "" : strategy.trim();
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
    }
}
