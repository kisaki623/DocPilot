package com.docpilot.backend.ai.rag;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class QdrantCollectionInfoRequestBuilder {

    public String path(String collection) {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("collection must not be blank");
        }
        return "/collections/" + URLEncoder.encode(collection.trim(), StandardCharsets.UTF_8);
    }
}
