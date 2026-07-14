package com.docpilot.backend.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RagRetrievalProperties {

    private boolean hybridEnabled = false;
    private boolean multiQueryEnabled = false;
    private int maxQueryVariants = 3;
    private double minSimilarityThreshold = 0.0;
    private int rrfK = 60;

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public void setHybridEnabled(boolean hybridEnabled) {
        this.hybridEnabled = hybridEnabled;
    }

    public boolean isMultiQueryEnabled() {
        return multiQueryEnabled;
    }

    public void setMultiQueryEnabled(boolean multiQueryEnabled) {
        this.multiQueryEnabled = multiQueryEnabled;
    }

    public int getMaxQueryVariants() {
        return maxQueryVariants;
    }

    public void setMaxQueryVariants(int maxQueryVariants) {
        if (maxQueryVariants < 1 || maxQueryVariants > 5) {
            throw new IllegalArgumentException("max-query-variants must be between 1 and 5");
        }
        this.maxQueryVariants = maxQueryVariants;
    }

    public double getMinSimilarityThreshold() {
        return minSimilarityThreshold;
    }

    public void setMinSimilarityThreshold(double minSimilarityThreshold) {
        if (minSimilarityThreshold < 0.0 || minSimilarityThreshold > 1.0) {
            throw new IllegalArgumentException("min-similarity-threshold must be between 0 and 1");
        }
        this.minSimilarityThreshold = minSimilarityThreshold;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        if (rrfK < 0) {
            throw new IllegalArgumentException("rrf-k must be non-negative");
        }
        this.rrfK = rrfK;
    }
}
