package com.docpilot.backend.ai.rag;

public interface EmbeddingModel {

    EmbeddingVector embed(String text);
}
