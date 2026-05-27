package com.docpilot.backend.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag.qa")
public class RagQaProperties {

    private boolean enabled = false;
    private int topK = 3;
    private int maxContextChars = 2000;
    private boolean fallbackEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("app.rag.qa.top-k must be positive.");
        }
        this.topK = topK;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        if (maxContextChars <= 0) {
            throw new IllegalArgumentException("app.rag.qa.max-context-chars must be positive.");
        }
        this.maxContextChars = maxContextChars;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
