# Offline RAG Retrieval Evaluation

- Mode: `offline`
- Embedding provider: `fake`
- Fallback reason: `qdrant_http_error`
- Sanitized: `true`

| Provider | Total | Positive hit rate | Avg retrieved | No-match | Empty doc | Isolation | Fallback |
| --- | ---: | ---: | ---: | --- | --- | --- | --- |
| in_memory | 5 | 1.0000 | 0.80 | true | true | true | false |
| qdrant_fake_server | 1 | 1.0000 | 1.00 | true | true | true | false |
| qdrant_fake_server_fallback | 1 | 0.0000 | 0.00 | true | true | true | true |

Artifacts are generated from synthetic fixtures only and intentionally omit source text.
