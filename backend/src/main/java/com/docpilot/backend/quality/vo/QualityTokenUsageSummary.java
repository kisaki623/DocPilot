package com.docpilot.backend.quality.vo;

import java.math.BigDecimal;

public record QualityTokenUsageSummary(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal estimatedCost
) {

    public static QualityTokenUsageSummary empty() {
        return new QualityTokenUsageSummary(null, null, null, null);
    }
}
